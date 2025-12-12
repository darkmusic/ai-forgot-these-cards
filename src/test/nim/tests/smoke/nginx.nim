import std/os
import ./smoke_api

discard """
  action: "run"
"""

# Nginx entrypoint (full stack). Override via BASE_URL_NGINX or BASE_URL.
let baseUrl = getEnv("BASE_URL_NGINX", getEnv("BASE_URL", "http://localhost:8086"))
let adminUser = getEnv("ADMIN_USER", "cards")
let adminPass = getEnv("ADMIN_PASS", "cards")
let nonAdminPass = getEnv("NONADMIN_PASS", "testpass")
let nonAdminHash = getEnv("NONADMIN_PASS_HASH_BCRYPTJS_2B", "$2b$10$cdHhlMdofgY0HJ1EYYXuK.6WqOXHcv9nzhHSCHnMkKXh1pwt0yWd6")

runLiveApiSmoke(
  baseUrl = baseUrl,
  adminUser = adminUser,
  adminPass = adminPass,
  nonAdminPass = nonAdminPass,
  nonAdminPassHashBcrypt2b = nonAdminHash,
  label = "Nginx"
)
