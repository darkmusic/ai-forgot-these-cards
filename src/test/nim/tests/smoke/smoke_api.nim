import std/httpclient, std/strformat, std/strutils, std/json, std/tables, std/uri, std/times, std/os

type Session* = object
  client*: HttpClient
  cookies*: Table[string, string]
  baseUrl*: string

proc initSession*(baseUrl: string): Session =
  result.client = newHttpClient()
  result.cookies = initTable[string, string]()
  result.baseUrl = baseUrl

proc cookieHeaderValue(cookies: Table[string, string]): string =
  var parts: seq[string] = @[]
  for k, v in cookies.pairs:
    parts.add(k & "=" & v)
  result = parts.join("; ")

proc updateCookieFromSetCookieHeader(cookies: var Table[string, string], setCookieHeader: string, name: string) =
  var startIdx = 0
  while true:
    let pos = setCookieHeader.find(name & "=", startIdx)
    if pos < 0:
      break
    let valueStart = pos + name.len + 1
    var valueEnd = setCookieHeader.find(';', valueStart)
    if valueEnd < 0:
      valueEnd = setCookieHeader.len
    let value = setCookieHeader[valueStart ..< valueEnd]
    if value.len > 0:
      cookies[name] = value
    startIdx = valueEnd

proc updateCookies(sess: var Session, resp: Response) =
  if not resp.headers.hasKey("set-cookie"):
    return
  let sc = resp.headers["set-cookie"]
  updateCookieFromSetCookieHeader(sess.cookies, sc, "JSESSIONID")
  updateCookieFromSetCookieHeader(sess.cookies, sc, "XSRF-TOKEN")

proc mergedHeaders(sess: Session, extra: HttpHeaders): HttpHeaders =
  result = newHttpHeaders()
  let cookieVal = cookieHeaderValue(sess.cookies)
  if cookieVal.len > 0:
    result["Cookie"] = cookieVal
  if extra != nil:
    for k, v in extra.pairs:
      result[k] = v

proc request*(sess: var Session, httpMethod: HttpMethod, path: string, body: string = "", extra: HttpHeaders = nil): Response =
  let url = sess.baseUrl & path
  let headers = mergedHeaders(sess, extra)
  result = sess.client.request(url, httpMethod = httpMethod, body = body, headers = headers)
  updateCookies(sess, result)

proc fetchCsrf*(sess: var Session): (string, string) =
  let resp = request(sess, HttpGet, "/api/csrf")
  assert resp.code == Http200
  let jsonBody = parseJson(resp.body)
  let csrfHeader = jsonBody["headerName"].getStr()
  let csrfToken = jsonBody["token"].getStr()
  assert csrfHeader.len > 0
  assert csrfToken.len > 0
  return (csrfHeader, csrfToken)

proc apiLogin*(sess: var Session, username: string, password: string) =
  let (csrfHeader, csrfToken) = fetchCsrf(sess)
  let formBody = "username=" & encodeUrl(username) & "&password=" & encodeUrl(password)
  let headers = newHttpHeaders({
    csrfHeader: csrfToken,
    "Content-Type": "application/x-www-form-urlencoded"
  })
  let resp = request(sess, HttpPost, "/api/login", body = formBody, extra = headers)
  assert resp.code == Http200

proc assertStatus*(code: HttpCode, expected: HttpCode, msg: string) =
  if code != expected:
    raise newException(AssertionDefect, msg & fmt" (expected {expected.int}, got {code.int})")

proc assertJsonLacks*(obj: JsonNode, key: string, msg: string) =
  if obj.kind == JObject and obj.hasKey(key):
    raise newException(AssertionDefect, msg)

proc requireJsonBool*(obj: JsonNode, key: string): bool =
  assert obj.kind == JObject
  assert obj.hasKey(key)
  result = obj[key].getBool()

proc requireJsonStr*(obj: JsonNode, key: string): string =
  assert obj.kind == JObject
  assert obj.hasKey(key)
  result = obj[key].getStr()

proc requireJsonInt*(obj: JsonNode, key: string): int =
  assert obj.kind == JObject
  assert obj.hasKey(key)
  result = obj[key].getInt()

proc requireRole*(obj: JsonNode, role: string) =
  assert obj.kind == JObject
  assert obj.hasKey("roles")
  let roles = obj["roles"]
  assert roles.kind == JArray
  var found = false
  for r in roles:
    if r.getStr() == role:
      found = true
  assert found

proc forbidRole*(obj: JsonNode, role: string) =
  assert obj.kind == JObject
  assert obj.hasKey("roles")
  let roles = obj["roles"]
  assert roles.kind == JArray
  for r in roles:
    assert r.getStr() != role

proc uniqueUsername*(prefix: string = "nonadmin_live_"): string =
  result = prefix & $getTime().toUnix() & "_" & $getTime().nanosecond

proc waitForHealthy*(baseUrl: string, timeoutMs: int = 30000, intervalMs: int = 500) =
  ## Wait until the app reports healthy (Spring Boot actuator) to reduce flakes
  ## when the stack just started (e.g., Hibernate schema init / SQLite file creation).
  var sess = initSession(baseUrl)
  let deadline = getTime() + initDuration(milliseconds = timeoutMs)
  var lastMsg = "(no response)"

  while true:
    try:
      let resp = request(sess, HttpGet, "/actuator/health")
      lastMsg = fmt"HTTP {resp.code.int} {resp.body}"
      if resp.code == Http200:
        try:
          let j = parseJson(resp.body)
          if j.kind == JObject and j.hasKey("status") and j["status"].getStr() == "UP":
            return
        except CatchableError:
          discard
    except OSError:
      lastMsg = "connection refused"

    if getTime() >= deadline:
      raise newException(AssertionDefect,
        fmt"Timed out waiting for {baseUrl} to become healthy. Last response: {lastMsg}")

    sleep(intervalMs)

proc runLiveApiSmoke*(
  baseUrl: string,
  adminUser: string = "cards",
  adminPass: string = "cards",
  nonAdminPass: string = "testpass",
  nonAdminPassHashBcrypt2b: string = "$2b$10$cdHhlMdofgY0HJ1EYYXuK.6WqOXHcv9nzhHSCHnMkKXh1pwt0yWd6",
  label: string = "Live API"
) =
  echo fmt"Running {label} smoke tests against: {baseUrl}"

  var admin = initSession(baseUrl)
  var nonadmin = initSession(baseUrl)

  # Make sure the server is fully started (not just accepting connections).
  waitForHealthy(baseUrl)

  # Fail fast with a helpful message if the stack isn't up.
  try:
    discard fetchCsrf(admin)
  except OSError:
    raise newException(AssertionDefect,
      fmt"Cannot reach {baseUrl} (connection refused). Start the containers for this test target.")
  except CatchableError:
    # If we get here, the host is reachable but the response was unexpected.
    raise

  var createdUserId = -1
  var createdUsername = ""
  var adminId = -1

  proc adminCleanup() =
    if createdUserId < 0:
      return
    try:
      let (csrfHeader, csrfToken) = fetchCsrf(admin)
      let headers = newHttpHeaders({csrfHeader: csrfToken})
      let resp = request(admin, HttpDelete, fmt"/api/user/{createdUserId}", extra = headers)
      assert resp.code in {Http200, Http204}
    except CatchableError:
      discard

  defer:
    adminCleanup()

  echo "[1/6] Login as admin and validate roles"
  apiLogin(admin, adminUser, adminPass)

  let currentAdminResp = request(admin, HttpGet, "/api/current-user")
  assert currentAdminResp.code == Http200
  let currentAdminJson = parseJson(currentAdminResp.body)
  requireRole(currentAdminJson, "ROLE_ADMIN")

  # `/api/current-user` doesn't include numeric id.
  let adminUserResp = request(admin, HttpGet, fmt"/api/user/username/{adminUser}")
  assert adminUserResp.code == Http200
  let adminUserJson = parseJson(adminUserResp.body)
  adminId = requireJsonInt(adminUserJson, "id")
  assert adminId > 0

  echo "[2/6] Create temporary non-admin user"
  createdUsername = uniqueUsername()

  let createPayload = %*{
    "username": createdUsername,
    "password_hash": nonAdminPassHashBcrypt2b,
    "admin": false,
    "active": true,
    "name": "Non Admin Live",
    "profile_pic_url": "/vite.svg"
  }

  let (adminCsrfHeader, adminCsrfToken) = fetchCsrf(admin)
  let createHeaders = newHttpHeaders({
    adminCsrfHeader: adminCsrfToken,
    "Content-Type": "application/json"
  })
  let createResp = request(admin, HttpPost, "/api/user", body = $createPayload, extra = createHeaders)
  if createResp.code notin {Http200, Http201}:
    raise newException(AssertionDefect,
      fmt"create user failed (expected 200/201, got {createResp.code.int})\nBody:\n{createResp.body}")
  let createdUserJson = parseJson(createResp.body)
  assertJsonLacks(createdUserJson, "password_hash", "password_hash leaked in create-user response")
  createdUserId = requireJsonInt(createdUserJson, "id")
  assert createdUserId > 0

  echo "[3/6] Login as non-admin and validate roles"
  apiLogin(nonadmin, createdUsername, nonAdminPass)

  let currentNonAdminResp = request(nonadmin, HttpGet, "/api/current-user")
  assert currentNonAdminResp.code == Http200
  let currentNonAdminJson = parseJson(currentNonAdminResp.body)
  requireRole(currentNonAdminJson, "ROLE_USER")
  forbidRole(currentNonAdminJson, "ROLE_ADMIN")

  echo "[4/6] Verify non-admin cannot GET other users"
  assertStatus(request(nonadmin, HttpGet, fmt"/api/user/{createdUserId}").code, Http200, "non-admin GET self should be 200")
  assertStatus(request(nonadmin, HttpGet, fmt"/api/user/{adminId}").code, Http403, "non-admin GET other user should be 403")
  assertStatus(request(nonadmin, HttpGet, fmt"/api/user/username/{adminUser}").code, Http403, "non-admin GET other by username should be 403")

  echo "[5/6] Verify non-admin PUT rules and no privilege escalation"
  let (naCsrfHeader, naCsrfToken) = fetchCsrf(nonadmin)
  let naHeaders = newHttpHeaders({
    naCsrfHeader: naCsrfToken,
    "Content-Type": "application/json"
  })

  let putOtherResp = request(nonadmin, HttpPut, fmt"/api/user/{adminId}", body = "{\"name\":\"hax\"}", extra = naHeaders)
  assertStatus(putOtherResp.code, Http403, "non-admin PUT other user should be 403")

  let putSelfPayload = %*{
    "id": adminId,
    "username": adminUser,
    "name": "Non Admin Updated",
    "admin": true,
    "active": false,
    "profile_pic_url": ""
  }

  let putSelfResp = request(nonadmin, HttpPut, fmt"/api/user/{createdUserId}", body = $putSelfPayload, extra = naHeaders)
  assertStatus(putSelfResp.code, Http200, "non-admin PUT self should be 200")

  let putSelfJson = parseJson(putSelfResp.body)
  assert requireJsonInt(putSelfJson, "id") == createdUserId
  assert requireJsonStr(putSelfJson, "username") == createdUsername
  assert requireJsonBool(putSelfJson, "admin") == false
  assert requireJsonBool(putSelfJson, "active") == true
  assert requireJsonStr(putSelfJson, "name") == "Non Admin Updated"
  assertJsonLacks(putSelfJson, "password_hash", "password_hash leaked in PUT response")

  echo "[6/6] Cleanup: delete temporary user"
  adminCleanup()
  createdUserId = -1

  echo fmt"OK: {label} smoke tests passed"
