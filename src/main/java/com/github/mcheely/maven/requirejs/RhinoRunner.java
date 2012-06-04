/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is Rhino code, released
 * May 6, 1998.
 *
 * The Initial Developer of the Original Code is
 * Netscape Communications Corporation.
 * Portions created by the Initial Developer are Copyright (C) 1997-1999
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU General Public License Version 2 or later (the "GPL"), in which
 * case the provisions of the GPL are applicable instead of those above. If
 * you wish to allow use of your version of this file only under the terms of
 * the GPL and not to allow others to use your version of this file under the
 * MPL, indicate your decision by deleting the provisions above and replacing
 * them with the notice and other provisions required by the GPL. If you do
 * not delete the provisions above, a recipient may use your version of this
 * file under either the MPL or the GPL.
 *
 * ***** END LICENSE BLOCK ***** */

package com.github.mcheely.maven.requirejs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

import org.codehaus.plexus.util.IOUtil;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.WrappedException;

/**
 * The BasicRhinoShell program.
 *
 * Can execute scripts interactively or in batch mode at the command line. An
 * example of controlling the JavaScript engine.
 *
 * @author Norris Boyd
 * @author Matthew Cheely
 */
public class RhinoRunner extends ScriptableObject {

    private static final long serialVersionUID = 3859222870741981547L;

    @Override
    public String getClassName() {
        return "global";
    }

    /**
     * Execute a js file.
     * @param includes other js files to include and parse before executing the main script.
     * @param mainScript the script to run.
     * @param args arguments that will be visible to the script.
     * @param globalVariables global js variables to set.
     * @param reporter error reporter.
     */
    public static void exec(File[] includes, File mainScript, Object[] args, Map<String, Object> globalVariables, ErrorReporter reporter) {
        // Associate a new Context with this thread
        Context cx = Context.enter();
        cx.setErrorReporter(reporter);
        try {
            // Initialize the standard objects (Object, Function, etc.)
            // This must be done before scripts can be executed.
            RhinoRunner runner = new RhinoRunner();
            cx.initStandardObjects(runner);

            // Define some global functions particular to the BasicRhinoShell.
            // Note
            // that these functions are not part of ECMA.
            String[] names = { "print", "load", "readFile", "warn", "getResourceAsStream" };
            runner.defineFunctionProperties(names, RhinoRunner.class, ScriptableObject.DONTENUM);

            for (File include : includes) {
                runner.processSource(cx, include);
            }
            
            // Set up "arguments" in the global scope to contain the command
            // line arguments after the name of the script to execute
            Object[] array;
            if (args.length == 0) {
                array = new Object[0];
            } else {
                int length = args.length;
                array = new Object[length];
                System.arraycopy(args, 0, array, 0, length);
            }
            Scriptable argsObj = cx.newArray(runner, array);
            
            runner.defineProperty("arguments", argsObj, ScriptableObject.DONTENUM);
            
            for (String key : globalVariables.keySet()) {
                runner.defineProperty(key, globalVariables.get(key), ScriptableObject.DONTENUM);
            }

            runner.processSource(cx, mainScript);
        } finally {
            Context.exit();
        }
    }
    
    /**
     * Print the string values of its arguments.
     *
     * This method is defined as a JavaScript function. Note that its arguments
     * are of the "varargs" form, which allows it to handle an arbitrary number
     * of arguments supplied to the JavaScript function.
     * 
     * @param cx the js execution context
     * @param thisObj ?
     * @param args ?
     * @param funObj ?
     */
    public static void print(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                System.out.print(" ");
            }

            // Convert the arbitrary JavaScript value into a string form.
            String s = Context.toString(args[i]);

            System.out.print(s);
        }
        System.out.println();
    }

    /**
     * Report a warning.
     * 
     * @param cx the js execution context
     * @param thisObj ?
     * @param args ?
     * @param funObj ?
     */
    public static void warn(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        String message = Context.toString(args[ 0 ]);
        int line = (int) Context.toNumber(args[ 1 ]);
        String source = Context.toString(args[ 2 ]);
        int column = (int) Context.toNumber(args[ 3 ]);
        cx.getErrorReporter().warning(message, null, line, source, column);
    }

    /**
     * Read a file from a path. Exposed to js execution environment.
     * 
     * @param path the path to the file to be read
     * @return the contents of the file as a string
     */
    public String readFile(String path) {
        try {
            InputStream inputStream;
            File file = new File(path);
            if (file.exists()) {
                inputStream = new FileInputStream(path);
            } else {
                inputStream = getClass().getClassLoader().getResourceAsStream(path);
            }
              
            return IOUtil.toString(inputStream);
        } catch (IOException exc) {
            throw new RuntimeException("wrap: " + exc.getMessage(), exc);
        }
    }

    /**
     * Load and execute a set of JavaScript source files.
     *This method is exposed to js execution environment.
     * 
     * @param cx the js execution context
     * @param thisObj ?
     * @param args ?
     * @param funObj ?
     */
    public static void load(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        RhinoRunner runner = (RhinoRunner) getTopLevelScope(thisObj);
        for (Object element : args) {
            runner.processSource(cx, Context.toString(element));
        }
    }
    
    /**
     * Get a classpath resource as an input stream. Exposed to js environment?
     * @param path the path to the resource
     * @return an input stream for the resource
     */
    public InputStream getResourceAsStream(String path) {
        File file = new File(path);
        if (file.exists()) {
            try {
                return new FileInputStream(path);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        } else {
            return getClass().getClassLoader().getResourceAsStream(path);
        }
    }

    private void processSource(Context cx, String path) {
        String filename = path;
        File file = new File(filename);
        
        if (file.exists()) {
            processSource(cx, file);
        } else {
            if (filename.startsWith(".")) {
                filename = filename.substring(1);
            }
            
            if (!filename.startsWith("/")) {
                filename = "/" + filename;
            }
            
            InputStreamReader in = new InputStreamReader(getClass().getResourceAsStream(filename));
            processSource(cx, in, filename);
        }
    }
    

    private void processSource(Context cx, File file) {
        if (file != null) {
            String filename = file.getAbsolutePath();
            InputStreamReader in = null;
            
            try {
                in = new FileReader(file);
            } catch (FileNotFoundException ex) {
                Context.reportError("Couldn't open file \"" + filename + "\".");
                return;
            }

            processSource(cx, in, filename);
        }
    }
    
    private void processSource(Context cx, InputStreamReader in, String filename) {
        try {
            cx.evaluateReader(this, in, filename, 1, null);
        } catch (WrappedException we) {
            System.err.println(we.getWrappedException().toString());
            we.printStackTrace();
        } catch (EvaluatorException ee) {
            System.err.println("js: " + ee.getMessage());
        } catch (JavaScriptException jse) {
            System.err.println("js: " + jse.getMessage());
        } catch (IOException ioe) {
            System.err.println(ioe.toString());
        } finally {
            try {
                in.close();
            } catch (IOException ioe) {
                System.err.println(ioe.toString());
            }
        }
    }
}