package com.darkmusic.aiforgotthesecards.web.contracts;

import lombok.Getter;
import lombok.Setter;


@Setter
@Getter
public class AiAskRequest {
    private String model;
    private String question;
    private Long userId;
}
