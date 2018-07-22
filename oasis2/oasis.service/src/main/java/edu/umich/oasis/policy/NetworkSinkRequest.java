package edu.umich.oasis.policy;
/*
 * Created by Davino Junior - dmtsj@{cin.ufpe.br, gmail.com}
 * at 07/22/2018 19:13
 */

public class NetworkSinkRequest extends SinkRequest {

    private String url;

    /**
     * Create a new sink network request.
     *
     * @param sinkName The name of the sink requested.
     * @param url The name of the endpoint url
     */
    public NetworkSinkRequest(String sinkName, String url) {
        super(sinkName);
        this.url = url;
    }

    public String getUrl() {
        return url;
    }
}
