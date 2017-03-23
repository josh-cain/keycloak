package org.keycloak.protocol.docker;

import org.jboss.logging.Logger;
import org.keycloak.common.Profile;
import org.keycloak.events.EventBuilder;
import org.keycloak.events.EventType;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientSessionModel;
import org.keycloak.models.RealmModel;
import org.keycloak.protocol.AuthorizationEndpointBase;
import org.keycloak.services.ErrorResponseException;
import org.keycloak.services.Urls;
import org.keycloak.services.util.CacheControlUtil;
import org.keycloak.utils.ProfileHelper;

import javax.ws.rs.GET;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

/**
 * Implements a docker-client understandable format.
 */
public class DockerEndpoint extends AuthorizationEndpointBase {
    protected static final Logger logger = Logger.getLogger(DockerEndpoint.class);

    private final EventType login;
    private String account;
    private String service;
    private String scope;
    private ClientModel client;
    private ClientSessionModel clientSession;

    public DockerEndpoint(final RealmModel realm, final EventBuilder event, final EventType login) {
        super(realm, event);
        this.login = login;
    }

    @GET
    public Response build() {
        ProfileHelper.requireFeature(Profile.Feature.DOCKER);

        final MultivaluedMap<String, String> params = uriInfo.getQueryParameters();

        account = params.getFirst(DockerAuthV2Protocol.ACCOUNT_PARAM);
        if (account == null) {
            logger.debug("Account parameter not provided by docker auth.  This is techincally required, but not actually used since " +
                    "username is provided by Basic auth header.");
        }
        service = params.getFirst(DockerAuthV2Protocol.SERVICE_PARAM);
        if (service == null) {
            throw new ErrorResponseException("invalid_request", "service parameter must be provided", Response.Status.BAD_REQUEST);
        }
        client = realm.getClientByClientId(service);
        if (client == null) {
            logger.errorv("Failed to lookup client given by service={0} parameter for realm: {1}.", service, realm.getName());
            throw new ErrorResponseException("invalid_client", "Client specified by 'service' parameter does not exist", Response.Status.BAD_REQUEST);
        }
        scope = params.getFirst(DockerAuthV2Protocol.SCOPE_PARAM);

        checkSsl();
        checkRealm();

        clientSession = session.sessions().createClientSession(realm, client);
        clientSession.setAuthMethod(DockerAuthV2Protocol.LOGIN_PROTOCOL);
        clientSession.setAction(ClientSessionModel.Action.AUTHENTICATE.name());

        // Docker specific stuff
        clientSession.setNote(DockerAuthV2Protocol.ACCOUNT_PARAM, account);
        clientSession.setNote(DockerAuthV2Protocol.SERVICE_PARAM, service);
        clientSession.setNote(DockerAuthV2Protocol.SCOPE_PARAM, scope);
        clientSession.setNote(DockerAuthV2Protocol.ISSUER, Urls.realmIssuer(uriInfo.getBaseUri(), realm.getName()));

        // So back button doesn't work
        CacheControlUtil.noBackButtonCacheControlHeader();

        return handleBrowserAuthenticationRequest(clientSession, new DockerAuthV2Protocol(session, realm, uriInfo, headers, event.event(login)), false, false);
    }
}
