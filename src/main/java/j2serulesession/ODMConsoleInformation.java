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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static j2serulesession.MessageCode.ODM_CONSOLE_INFORMATION_CLASSLOADER_RESOURCE_NOT_FOUND;
import static j2serulesession.MessageCode.ODM_CONSOLE_INFORMATION_ERROR_UNEXPECTED;
import static j2serulesession.MessageCode.ODM_CONSOLE_INFORMATION_FILE_NOT_FOUND;
import static j2serulesession.MessageCode.ODM_CONSOLE_INFORMATION_READ_FROM_CLASSLOADER;
import static j2serulesession.MessageCode.ODM_CONSOLE_INFORMATION_READ_FROM_FILE;
import static j2serulesession.MessageCode.ODM_CONSOLE_INFORMATION_NOT_FOUND;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;


class ODMConsoleInformation {

    private URL url;

    private String userName;

    private String password;

    private static final Logger LOGGER = Logger.getLogger(ODMConsoleInformation.class.getName());

    private static final MessageFormatter formatter = new MessageFormatter();

    ODMConsoleInformation(URL url, String userName, String password) {
        setURL(url);
        setUserName(userName);
        setPassword(password);
    }

    ODMConsoleInformation() {
    }

    static ODMConsoleInformation readODMConsoleInformation(InputStream inputStream) throws JsonParseException,
            JsonMappingException,
            IOException {
        return new ObjectMapper().readValue(inputStream, ODMConsoleInformation.class);
    }

    static ODMConsoleInformation readODMConsoleInformation(String resourceName) throws JSONFileContentException {
        File file = new File(resourceName);
        try {
            if (file.exists()) {
                try (InputStream inputStream = file.toURI().toURL().openStream()) {
                    ODMConsoleInformation odmConsoleInformation = readODMConsoleInformation(inputStream);
                    info(ODM_CONSOLE_INFORMATION_READ_FROM_FILE, resourceName);
                    return odmConsoleInformation;
                }
            }
            warning(ODM_CONSOLE_INFORMATION_FILE_NOT_FOUND, resourceName);
            try (InputStream inputStream = ODMConsoleInformation.class.getClassLoader().getResourceAsStream(
                    resourceName)) {
                if (inputStream != null) {
                    ODMConsoleInformation odmConsoleInformation = readODMConsoleInformation(inputStream);
                    info(ODM_CONSOLE_INFORMATION_READ_FROM_CLASSLOADER, resourceName);
                    return odmConsoleInformation;
                } else {
                    info(ODM_CONSOLE_INFORMATION_CLASSLOADER_RESOURCE_NOT_FOUND, resourceName);
                }
            }
        } catch (JsonParseException exception) {
            throw new InvalidJSONSyntax(resourceName, exception);
        } catch (JsonMappingException exception) {
            throw new InvalidJSONContent(resourceName, exception);
        } catch (IOException exception) {
            String errorMessage = getMessage(ODM_CONSOLE_INFORMATION_ERROR_UNEXPECTED, resourceName,
                    exception.getMessage());
            throw new JSONFileContentException(errorMessage, exception);
        }
        String errorMessage = getMessage(ODM_CONSOLE_INFORMATION_NOT_FOUND, resourceName);
        throw new IllegalArgumentException(errorMessage);
    }

    private static void info(String key, Object... arguments) {
        log(INFO, key, arguments);
    }

    private static void log(Level level, String key, Object... arguments) {
        LOGGER.log(level, getMessage(key, arguments));
    }

    private static String getMessage(String key, Object... arguments) {
        return formatter.getMessage(key, arguments);
    }

    private static void warning(String key, Object... arguments) {
        log(WARNING, key, arguments);
    }

    String getUserName() {
        return userName;
    }

    String getPassword() {
        return password;
    }

    URL getURL() {
        return url;
    }

    void setURL(URL url) {
        this.url = url;
    }

    void setUserName(String userName) {
        this.userName = userName;
    }

    void setPassword(String password) {
        this.password = password;
    }
}
