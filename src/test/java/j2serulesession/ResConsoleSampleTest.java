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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.http.conn.HttpHostConnectException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;


/**
 * Class responsible for testing the ResConsoleSample.main() behaviour
 * 
 *
 */
public class ResConsoleSampleTest {

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog();

    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    @Test
    public void testNoRulesetPath() throws Exception {
        exit.expectSystemExitWithStatus(1);
        String argLine = "-j test_RES.json";
        RESConsoleSample.main(argLine.split(" "));
    }

    @Test
    public void testInvalidRulesetPath() throws Exception {
        exit.expectSystemExitWithStatus(1);
        String argLine = "foo/bar";
        RESConsoleSample.main(argLine.split(" "));
    }

    @Test
    public void testJSONFileNotFound() throws Exception {
        exit.expectSystemExitWithStatus(1);
        String argLine = "-j blablah /foo/bar";
        RESConsoleSample.main(argLine.split(" "));
    }

    @Test
    public void testDefaultJSONFile() throws Exception {
        Class<HttpHostConnectException> expecledExceptionClass = HttpHostConnectException.class;
        try {
            String argLine = "/test_deployment/loan_validation_with_score_and_grade";
            RESConsoleSample.main(argLine.split(" "));
            fail("A " + expecledExceptionClass.getName() + " should have been thrown");
        } catch (Exception exception) {
            assertEquals("Class of exception when reading RES Console URL and credentials from default location",
                    expecledExceptionClass, exception.getClass());
        }
    }

    @Test
    public void testJSONFileWithInvalidSyntax() throws Exception {
        try {
            exit.expectSystemExitWithStatus(2);
            String argLine = "--jsonFile invalid.json /test_deployment/loan_validation_with_score_and_grade";
            RESConsoleSample.main(argLine.split(" "));
            String expectedErrorMessage = "The syntax of the JSON file 'invalid.json''s content is incorrect. The following error occurred when reading it";
            assertTrue("", systemErrRule.getLog().contains(expectedErrorMessage));
        } finally {
            systemErrRule.clearLog();
        }
    }

    @Test
    public void testJSONFileUnexpectedContent() throws Exception {
        try {
            exit.expectSystemExitWithStatus(2);
            String argLine = "--jsonFile unexpected.json /test_deployment/loan_validation_with_score_and_grade";
            RESConsoleSample.main(argLine.split(" "));
            String expectedErrorMessage = "The content of the JSON file 'unexpected.json' cannot be mapped to an instance of the j2serulesession.ODMConsoleInformation class. The following error occurred when reading it:";
            assertTrue("", systemErrRule.getLog().contains(expectedErrorMessage));
        } finally {
            systemErrRule.clearLog();
        }
    }
}
