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

import static j2serulesession.MessageCode.ODM_CONSOLE_INFORMATION_ERROR_JSON_MAPPING;
import static j2serulesession.MessageCode.ODM_CONSOLE_INFORMATION_ERROR_JSON_SYNTAX;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;


class JSONFileContentException extends Exception {

    private static final long serialVersionUID = 1L;

    JSONFileContentException(String message, IOException exception) {
        super(message, exception);
    }
}

class InvalidJSONSyntax extends JSONFileContentException {

    private static final long serialVersionUID = 1L;

    InvalidJSONSyntax(String resourceName, JsonParseException exception) {
        super(new MessageFormatter().getMessage(ODM_CONSOLE_INFORMATION_ERROR_JSON_SYNTAX, resourceName,
                exception.getMessage()), exception);
    }
}

class InvalidJSONContent extends JSONFileContentException {

    private static final long serialVersionUID = 1L;

    InvalidJSONContent(String resourceName, JsonMappingException exception) {
        super(new MessageFormatter().getMessage(ODM_CONSOLE_INFORMATION_ERROR_JSON_MAPPING, resourceName,
                exception.getMessage()), exception);
    }
}
