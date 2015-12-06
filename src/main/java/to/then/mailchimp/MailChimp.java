package to.then.mailchimp;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.jboss.resteasy.client.jaxrs.BasicAuthentication;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.engines.ApacheHttpClient4Engine;
import org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider;

public class MailChimp implements Function<ObjectNode, ObjectNode> {

    private final ObjectMapper mapper;
    private final Client client;
    private final Map<String, ObjectNode> schemas;

    public MailChimp() {
        this(new ResteasyClientBuilder().httpEngine(
                new ApacheHttpClient4Engine(HttpClients.custom()
                        .setConnectionManager(new PoolingHttpClientConnectionManager())
                        .build()))
                .register(ResteasyJackson2Provider.class).build());
    }

    public MailChimp(Client client) {
        this.client = client;
        mapper = new ObjectMapper();
        schemas = new LinkedHashMap();
    }

    public void apply(InputStream source, OutputStream result, Context context) throws IOException {
        JsonNode request = mapper.readTree(source);
        mapper.writeValue(result, apply((ObjectNode) request));
    }

    @Override
    public ObjectNode apply(ObjectNode request) {
        JsonNode authorization = request.get("authorization");
        request.remove("authorization");
        String region = request.get("region").textValue();
        request.remove("region");
        String methodName = request.fieldNames().next();
        String[] methodParts = methodName.split("::");
        String schemaName = methodParts[0].replaceAll("\\.", "/");
        String rel = methodParts[1];
        ObjectNode schema = getSchema(schemaName, region);
        ObjectNode instance = (ObjectNode) request.get(methodName);
        Map instanceMap = mapper.convertValue(instance, Map.class);

        JsonNode link = null;
        for (JsonNode nextLink : schema.at("/links")) {
            if (nextLink.get("rel").textValue().equals(rel)) {
                link = nextLink;
            }
        }
        if (link == null) {
            throw new IllegalArgumentException("Unknown rel: \"" + rel + "\"");
        }

        String href = link.at("/href").asText();
        String method = link.at("/method").asText();
        JsonNode targetSchema = link.at("/targetSchema");

        UriBuilder uriBuilder = UriBuilder.fromUri(href);
        if (method.equals("GET")) {
            instance.fieldNames().forEachRemaining((fieldName) -> {
                uriBuilder.queryParam(fieldName, instance.get(fieldName).asText());
            });
        }
        URI uri = uriBuilder.buildFromMap(instanceMap);
        
        WebTarget target = client.target(uri);
        if (authorization.has("apiKey")) {
            target.register(new BasicAuthentication("any", authorization.get("apiKey").textValue()));
        }
        
        Builder invocation = target.request(MediaType.APPLICATION_JSON_TYPE);
        ObjectNode result = null;
        if (method.equals("GET")) {
            result = invocation.get(ObjectNode.class);
        } else if (method.equals("PUT")) {
            result = invocation.put(Entity.json(instanceMap), ObjectNode.class);
        } else if (method.equals("POST")) {
            result = invocation.post(Entity.json(instanceMap), ObjectNode.class);
        } else if (method.equals("PATCH")) {
            invocation.header("X-HTTP-Method-Override", "PATCH");
            result = invocation.post(Entity.json(instanceMap), ObjectNode.class);
        } else if (method.equals("DELETE")) {
            result = invocation.delete(ObjectNode.class);
        }
        return result;
    }

    private ObjectNode getSchema(String schemaPath, String region) {
        if (!schemaPath.endsWith(".json")) {
            schemaPath += ".json";
        }
        schemas.computeIfAbsent(schemaPath, (path) -> {
            URI uri = UriBuilder.fromUri("https://{region}.api.mailchimp.com/schema/3.0").path(path).build(region);
            ObjectNode schema = client.target(uri).request(MediaType.APPLICATION_JSON_TYPE).get(ObjectNode.class);
            return schema;
        });
        return schemas.get(schemaPath);
    }
}
