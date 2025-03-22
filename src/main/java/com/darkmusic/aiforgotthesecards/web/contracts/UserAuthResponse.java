package com.darkmusic.aiforgotthesecards.web.contracts;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
public class UserAuthResponse {
    private boolean authenticated;
    private List<String> roles;
    private String username;

    public UserAuthResponse() {
        roles = new ArrayList<>();
    }
}
