package nz.co.trademe;

import org.scribe.builder.api.DefaultApi10a;
import org.scribe.model.Token;

public class TradeMeApi extends DefaultApi10a
{
    @Override
    public String getRequestTokenEndpoint()
    {
        return "https://secure.trademe.co.nz/Oauth/RequestToken";
    }

    @Override
    public String getAccessTokenEndpoint()
    {
        return "https://secure.trademe.co.nz/Oauth/AccessToken";
    }

    @Override
    public String getAuthorizationUrl(Token token)
    {
        return "https://secure.trademe.co.nz/Oauth/Authorize?oauth_token=" + token.getToken();
    }
}