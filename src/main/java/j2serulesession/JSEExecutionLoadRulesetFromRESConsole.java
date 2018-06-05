/*
 *
 *   Copyright IBM Corp. 2018
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package j2serulesession;

import static ilog.rules.res.model.IlrEngineType.CRE;
import static ilog.rules.res.model.IlrEngineType.DE;
import static ilog.rules.res.session.config.IlrPersistenceType.MEMORY;
import static j2serulesession.MessageCode.RUNTIME_EXCEPTION_HTTP_RESPONSE;
import static j2serulesession.MessageCode.RUNTIME_EXCEPTION_RULESET_NOT_FOUND;

import ilog.rules.archive.IlrRulesetArchive.RulesetArchiveException;
import ilog.rules.res.model.IlrAlreadyExistException;
import ilog.rules.res.model.IlrEngineType;
import ilog.rules.res.model.IlrFormatException;
import ilog.rules.res.model.IlrMutableRepository;
import ilog.rules.res.model.IlrMutableRuleAppInformation;
import ilog.rules.res.model.IlrMutableRulesetArchiveInformation;
import ilog.rules.res.model.IlrPath;
import ilog.rules.res.model.IlrRepositoryFactory;
import ilog.rules.res.model.IlrVersion;
import ilog.rules.res.model.archive.IlrArchiveException;
import ilog.rules.res.session.IlrJ2SESessionFactory;
import ilog.rules.res.session.IlrSessionCreationException;
import ilog.rules.res.session.IlrSessionException;
import ilog.rules.res.session.IlrSessionRequest;
import ilog.rules.res.session.IlrSessionResponse;
import ilog.rules.res.session.IlrStatelessSession;
import ilog.rules.res.session.config.IlrSessionFactoryConfig;
import ilog.rules.res.session.config.IlrXUConfig;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import loan.Borrower;
import loan.LoanRequest;
import loan.Report;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;


@SuppressWarnings("deprecation")
class RulesetInfo {

    private final String headerValue;

    private final IlrPath rulesetPath;

    private final byte[] bytes;

    RulesetInfo(IlrPath rulesetPath, String headerValue, byte[] bytes) {
        this.rulesetPath = rulesetPath;
        this.headerValue = headerValue;
        this.bytes = bytes;
    }

    String getHeaderValue() {
        return headerValue;
    }

    byte[] getBytes() {
        return bytes;
    }

    public IlrPath getRulesetPath() {
        return rulesetPath;
    }
}

class VersionedMember {

    private String version;

    private String id;

    private String name;

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
class RuleApp extends VersionedMember {

    private List<Ruleset> rulesets;

    public List<Ruleset> getRulesets() {
        return rulesets;
    }

    public void setRulesets(List<Ruleset> rulesets) {
        this.rulesets = rulesets;
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
class Ruleset extends VersionedMember {
}

@SuppressWarnings("deprecation")
public class JSEExecutionLoadRulesetFromRESConsole {

    private final MessageFormatter formatter = new MessageFormatter();

    private static final String ARCHIVE = "/archive"; // No_i18n

    private static final String API_V1_RULEAPPS = "api/v1/ruleapps/"; // No_i18n

    private static final String SLASH = "/"; // No_i18n

    private static final String APPLICATION_JSON = "application/json"; // No_i18n

    private static final String CONTENT_TYPE = "Content-Type"; // No_i18n

    private static final String DSAR = "dsar"; // No_i18n

    private static final String CONTENT_DISPOSITION = "Content-Disposition"; // No_i18n

    private final HttpClientContext httpClientContext;

    private final CloseableHttpClient client;

    private final String uri;

    private final IlrJ2SESessionFactory factory;

    public JSEExecutionLoadRulesetFromRESConsole(ODMConsoleInformation resConsoleInformation) {
        this(createJ2SESessionFactory(), resConsoleInformation);
    }

    private static IlrJ2SESessionFactory createJ2SESessionFactory() {
        IlrSessionFactoryConfig factoryConfig = IlrJ2SESessionFactory.createDefaultConfig();
        IlrXUConfig xuConfig = factoryConfig.getXUConfig();
        xuConfig.setLogAutoFlushEnabled(true);
        xuConfig.getPersistenceConfig().setPersistenceType(MEMORY);
        xuConfig.getManagedXOMPersistenceConfig().setPersistenceType(MEMORY);
        return new IlrJ2SESessionFactory(factoryConfig);
    }

    public JSEExecutionLoadRulesetFromRESConsole(IlrJ2SESessionFactory factory,
            ODMConsoleInformation resConsoleInformation) {
        this.factory = factory;
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        URL url = resConsoleInformation.getURL();
        HttpHost httpHost = new HttpHost(url.getHost(), url.getPort(), url.getProtocol());
        credsProvider.setCredentials(
                new AuthScope(httpHost.getHostName(), httpHost.getPort()),
                new UsernamePasswordCredentials(resConsoleInformation.getUserName(), resConsoleInformation
                        .getPassword()));
        AuthCache authCache = new BasicAuthCache();
        BasicScheme basicAuth = new BasicScheme();
        authCache.put(httpHost, basicAuth);
        httpClientContext = HttpClientContext.create();
        httpClientContext.setCredentialsProvider(credsProvider);
        httpClientContext.setAuthCache(authCache);
        client = HttpClientBuilder.create().build();
        uri = httpHost.toURI().concat(url.getFile());
    }

    public void release() {
        factory.release();
    }

    public void loadRulesetFromRESConsoleThenExecuteRuleset(IlrPath rulesetPath) throws ClientProtocolException,
            IOException,
            IlrSessionCreationException,
            IlrSessionException,
            IlrArchiveException,
            IlrAlreadyExistException,
            IlrFormatException {
        RulesetInfo rulesetInfo = getRulesetFromRESConsole(rulesetPath);
        addRulesetToRepository(rulesetInfo);
        executeRuleset(rulesetPath);

    }

    private void addRulesetToRepository(RulesetInfo rulesetInfo) throws IlrSessionException,
            IlrSessionCreationException,
            IlrFormatException,
            IlrAlreadyExistException,
            IOException {
        IlrPath rulesetPath = rulesetInfo.getRulesetPath();
        IlrRepositoryFactory repositoryFactory = factory.createManagementSession().getRepositoryFactory();
        IlrMutableRuleAppInformation ruleApp = repositoryFactory.createRuleApp(rulesetPath.getRuleAppName(),
                rulesetPath.getRuleAppVersion());
        IlrMutableRulesetArchiveInformation ruleset = repositoryFactory.createRuleset(rulesetPath.getRulesetName(),
                rulesetPath.getRulesetVersion());
        ruleApp.addRuleset(ruleset);
        // DOES NOT WORK: CAN'T CURRENTLY INFER ENGINE TYPE FROM HEADER CONTENT
        // 'filename' IS ALWAYS SET TO 'ruleset.jar' :-/
        IlrEngineType engineType = rulesetInfo.getHeaderValue().endsWith(DSAR) ? DE : CRE;
        byte[] bytes = rulesetInfo.getBytes();
        try (ByteArrayInputStream creByteStream = new ByteArrayInputStream(bytes)) {
            ruleset.setRESRulesetArchive(engineType, creByteStream);
        } catch (RulesetArchiveException exception) {
            try (ByteArrayInputStream deByteStream = new ByteArrayInputStream(bytes)) {
                ruleset.setRESRulesetArchive(DE, deByteStream);
            }
        }
        IlrMutableRepository repository = repositoryFactory.createRepository();
        repository.addRuleApp(ruleApp);
    }

    private RulesetInfo getRulesetFromRESConsole(IlrPath rulesetPath) throws IOException,
            ClientProtocolException,
            IlrFormatException {
        IlrPath canonicalPath = getCanonicalPath(rulesetPath);
        if (canonicalPath == null) {
            String errorMessage = getMessage(RUNTIME_EXCEPTION_RULESET_NOT_FOUND, rulesetPath, uri);
            throw new RuntimeException(errorMessage);
        }
        HttpGet getMethod = getGETRulesetArchiveMethod(uri, canonicalPath);
        try (CloseableHttpResponse httpResponse = client.execute(getMethod, httpClientContext)) {
            StatusLine statusLine = httpResponse.getStatusLine();
            int statusCode = statusLine.getStatusCode();
            if (statusCode == HttpStatus.SC_OK) {
                Header header = httpResponse.getFirstHeader(CONTENT_DISPOSITION);
                if (header != null) {
                    String headerValue = header.getValue();
                    try (InputStream inputStream = httpResponse.getEntity().getContent()) {
                        if (inputStream != null) {
                            return new RulesetInfo(canonicalPath, headerValue, toByteArray(inputStream));
                        }
                    }
                }
            }
            String errorMessage = getMessage(RUNTIME_EXCEPTION_HTTP_RESPONSE, getMethod, statusLine);
            throw new RuntimeException(errorMessage);
        }
    }

    private IlrPath getCanonicalPath(IlrPath rulesetPath) throws ClientProtocolException,
            IOException,
            IlrFormatException {
        HttpGet getMethod = getGETRuleAppsHttpMethod(uri, rulesetPath);
        try (CloseableHttpResponse httpResponse = client.execute(getMethod, httpClientContext)) {
            StatusLine statusLine = httpResponse.getStatusLine();
            int statusCode = statusLine.getStatusCode();
            if (statusCode == HttpStatus.SC_OK) {
                try (InputStream inputStream = httpResponse.getEntity().getContent()) {
                    if (inputStream != null) {
                        RuleApp[] ruleApps = new ObjectMapper().readValue(inputStream, RuleApp[].class);
                        return getCanonicalRulesetPath(rulesetPath, ruleApps);
                    }
                }
            }
            String errorMessage = getMessage(RUNTIME_EXCEPTION_HTTP_RESPONSE, getMethod, statusLine);
            throw new RuntimeException(errorMessage);
        }
    }

    private IlrPath getCanonicalRulesetPath(IlrPath rulesetPath, RuleApp[] ruleApps) throws IlrFormatException {
        if (ruleApps.length == 0) {
            return null;
        }
        List<IlrPath> rulesetPaths = new ArrayList<>();
        for (RuleApp ruleApp : ruleApps) {
            for (Ruleset ruleset : ruleApp.getRulesets()) {
                rulesetPaths.add(IlrPath.parsePath(SLASH + ruleset.getId()));
            }
        }
        // FILTER OUT RULESET PATHS NOT MATCHING THE PROVIDED RULEAPP VERSION, IF ANY
        IlrVersion ruleAppVersion = rulesetPath.getRuleAppVersion();
        if (ruleAppVersion != null) {
            Iterator<IlrPath> iterator = rulesetPaths.iterator();
            while (iterator.hasNext()) {
                IlrPath currentRulesetPath = iterator.next();
                if (!ruleAppVersion.equals(currentRulesetPath.getRuleAppVersion())) {
                    iterator.remove();
                }
            }
        }
        if (rulesetPaths.isEmpty()) {
            return null;
        }
        // FILTER OUT RULESET PATHS NOT MATCHING THE PROVIDED RULESET NAME
        String rulesetName = rulesetPath.getRulesetName();
        Iterator<IlrPath> iterator = rulesetPaths.iterator();
        while (iterator.hasNext()) {
            IlrPath currentRulesetPath = iterator.next();
            if (!rulesetName.equals(currentRulesetPath.getRulesetName())) {
                iterator.remove();
            }
        }
        // FILTER OUT RULESET PATHS NOT MATCHING THE PROVIDED RULESET VERSION, IF ANY
        IlrVersion rulesetVersion = rulesetPath.getRulesetVersion();
        if (rulesetVersion != null) {
            iterator = rulesetPaths.iterator();
            while (iterator.hasNext()) {
                IlrPath currentRulesetPath = iterator.next();
                if (!rulesetVersion.equals(currentRulesetPath.getRulesetVersion())) {
                    iterator.remove();
                }
            }
        }
        if (rulesetPaths.isEmpty()) {
            return null;
        }
        Collections.sort(rulesetPaths, new Comparator<IlrPath>() {

            @Override
            public int compare(IlrPath lhs, IlrPath rhs) {
                IlrVersion lhsRuleAppVersion = lhs.getRuleAppVersion();
                IlrVersion rhsRuleAppVersion = rhs.getRuleAppVersion();
                int ruleAppVersionCompareTo = lhsRuleAppVersion.compareTo(rhsRuleAppVersion);
                if (ruleAppVersionCompareTo != 0) {
                    return -ruleAppVersionCompareTo;
                }
                return -lhs.getRulesetVersion().compareTo(rhs.getRulesetVersion());
            }
        });
        return rulesetPaths.get(0);
    }

    private byte[] toByteArray(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        while (true) {
            int length = inputStream.read(buffer);
            if (length == -1) {
                break;
            }
            outputStream.write(buffer, 0, length);
        }
        return outputStream.toByteArray();
    }

    private HttpGet getGETRulesetArchiveMethod(String uri, IlrPath rulesetPath) {
        StringBuffer tmpURL = getRuleAppsRESTAPIURL(uri, rulesetPath.getRuleAppName());
        tmpURL.append(SLASH).append(rulesetPath.getRuleAppVersion());
        tmpURL.append(SLASH).append(rulesetPath.getRulesetName());
        tmpURL.append(SLASH).append(rulesetPath.getRulesetVersion()).append(ARCHIVE);
        return new HttpGet(tmpURL.toString());
    }

    private StringBuffer getRuleAppsRESTAPIURL(String uri, String ruleAppName) {
        StringBuffer tmpURL = new StringBuffer(uri);
        if (!uri.endsWith(SLASH)) {
            tmpURL.append(SLASH);
        }
        tmpURL.append(API_V1_RULEAPPS).append(ruleAppName);
        return tmpURL;
    }

    private String getMessage(String key, Object... arguments) {
        return formatter.getMessage(key, arguments);
    }

    private HttpGet getGETRuleAppsHttpMethod(String uri, IlrPath rulesetPath) {
        StringBuffer tmpURL = getRuleAppsRESTAPIURL(uri, rulesetPath.getRuleAppName());
        HttpGet httpGet = new HttpGet(tmpURL.toString());
        httpGet.setHeader(CONTENT_TYPE, APPLICATION_JSON);
        return httpGet;
    }

    public void executeRuleset(IlrPath rulesetPath) throws IlrFormatException,
            IlrSessionCreationException,
            IlrSessionException {
        // Create a session request object
        IlrSessionRequest sessionRequest = factory.createRequest();
        sessionRequest.setTraceEnabled(false);
        sessionRequest.getTraceFilter().setInfoAllFilters(false);
        sessionRequest.setRulesetPath(rulesetPath);
        // Ensure latest version of the ruleset is taken into account
        sessionRequest.setForceUptodate(true);
        // Set the input parameters for the execution of the rules
        Map<String, Object> inputParameters = new HashMap<String, Object>();
        java.util.Date birthDate = loan.DateUtil.makeDate(1950, 1, 1);
        // Set borrower ruleset parameter
        loan.Borrower borrower = new Borrower("Smith", "John", birthDate, "123121234");
        borrower.setZipCode("12345");
        borrower.setCreditScore(200);
        borrower.setYearlyIncome(20000);
        // Set loan ruleset parameter
        inputParameters.put("borrower", borrower);
        loan.LoanRequest loan = new LoanRequest(new Date(), 48, 100000, 1.2);
        inputParameters.put("loan", loan);
        sessionRequest.setInputParameters(inputParameters);
        // Create the stateless rule session.
        IlrStatelessSession session = factory.createStatelessSession();
        // Execute rules
        IlrSessionResponse sessionResponse = session.execute(sessionRequest);
        // Display the report
        Report report = (Report) (sessionResponse.getOutputParameters().get("report"));
        System.out.println(report.toString());
    }
}
