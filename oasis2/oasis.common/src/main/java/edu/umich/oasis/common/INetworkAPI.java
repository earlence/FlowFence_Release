package edu.umich.oasis.common;

/*
 * Created by Davino Junior - dmtsj@{cin.ufpe.br, gmail.com}
 * at 07/25/2018 13:27
 */

import java.util.Map;

public interface INetworkAPI {
    String get(String url);
    String getWithQuery(String url, Map query);
    String post(String url, Map body);
}
