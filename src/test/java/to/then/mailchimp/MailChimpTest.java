package to.then.mailchimp;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import org.jboss.resteasy.client.jaxrs.BasicAuthentication;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.*;

public class MailChimpTest {
    
    private Client http;
    private MailChimp mail;
    private ObjectMapper mapper;
    
    @Before
    public void before() throws IOException {
        mapper = new ObjectMapper();
        http = mock(Client.class); 
        mail = new MailChimp(http);
    }
    
    @Test
    public void memberUpsert() throws IOException {
        
        WebTarget target = mock(WebTarget.class);
        when(target.register(any(BasicAuthentication.class))).thenReturn(target);
        Invocation.Builder builder = mock(Invocation.Builder.class);
        when(target.request(MediaType.APPLICATION_JSON_TYPE)).thenReturn(builder);
        Response response = mock(Response.class);
        when(response.getStatusInfo()).thenReturn(Response.Status.OK);
        when(builder.put(any(Entity.class))).thenReturn(response);
        when(http.target(UriBuilder.fromUri("https://us1.api.mailchimp.com/3.0/lists/789abcdef/members/0123456789abcdef").build())).thenReturn(target);
        
        mockGet("https://us1.api.mailchimp.com/schema/3.0/Lists/Members/Instance.json", "lists-members-instance-schema.json");
        
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        mail.apply(MailChimpTest.class.getResourceAsStream("member-upsert.json"), result, mock(Context.class));
    }
    
    @Test
    public void memberActivity() throws IOException {
        mockGet("https://us1.api.mailchimp.com/schema/3.0/Lists/Members/Instance.json", "lists-members-instance-schema.json");
        mockGet("https://us1.api.mailchimp.com/3.0/lists/789abcdef/members/0123456789abcdef/activity?id=0123456789abcdef&list_id=789abcdef", "member-activity-result.json");
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        mail.apply(MailChimpTest.class.getResourceAsStream("member-activity.json"), result, mock(Context.class));
        Assert.assertEquals("{\"activity\":[],\"email_id\":\"0123456789abcdef\",\"list_id\":\"789abcdef\",\"_links\":[{\"rel\":\"self\",\"href\":\"https://us1.api.mailchimp.com/3.0/lists/789abcdef/members/0123456789abcdef/activity\",\"method\":\"GET\",\"targetSchema\":\"https://us1.api.mailchimp.com/schema/3.0/Lists/Members/Activity/Collection.json\"},{\"rel\":\"parent\",\"href\":\"https://us1.api.mailchimp.com/3.0/lists/789abcdef/members/0123456789abcdef\",\"method\":\"GET\",\"targetSchema\":\"https://us1.api.mailchimp.com/schema/3.0/Lists/Members/Instance.json\"}],\"total_items\":0}",
               new String(result.toByteArray()));
    }
    
    private void mockGet(String uri, String resourceName) {
        try {
            WebTarget target = mock(WebTarget.class);
            Invocation.Builder builder = mock(Invocation.Builder.class);
            when(target.request(MediaType.APPLICATION_JSON_TYPE)).thenReturn(builder);
            Response response = mock(Response.class);
            when(response.getStatusInfo()).thenReturn(Response.Status.OK);
            when(builder.get(ObjectNode.class)).thenReturn((ObjectNode) mapper.readTree(MailChimpTest.class.getResourceAsStream(resourceName)));
            when(http.target(UriBuilder.fromUri(uri).build())).thenReturn(target);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    
    
}
