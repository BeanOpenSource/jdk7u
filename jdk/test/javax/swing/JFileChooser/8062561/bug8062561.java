/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.concurrent.TimeUnit;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileSystemView;

/**
 * @test
 * @bug 8062561
 * @summary File system view returns null default directory
 * @run main/othervm bug8062561 GENERATE_POLICY
 * @run main/othervm/policy=security.policy bug8062561 CHECK_DEFAULT_DIR run
 */
public class bug8062561 {

    private static final String POLICY_FILE = "security2.policy";
    private static volatile boolean fileChooserIsShown = false;

    public static void main(String[] args) throws Exception {

        String test = args[0];

        if ("GENERATE_POLICY".equals(test)) {
            generatePolicyFile();
        } else if ("CHECK_DEFAULT_DIR".equals(test)) {
            checkDefaultDirectory();
        } else if ("CHECK_FILE_CHOOSER".equals(test)) {
            checkFileChooser();
        } else {
            throw new RuntimeException("Wrong argument!");
        }
    }

    private static void checkDefaultDirectory() {
        if (System.getSecurityManager() == null) {
            throw new RuntimeException("Security manager is not set!");
        }

        File defaultDirectory = FileSystemView.getFileSystemView().
                getDefaultDirectory();
        if (defaultDirectory != null) {
            throw new RuntimeException("File system default directory is null!");
        }
    }
    private static volatile JFileChooser fileChooser;

    private static void checkFileChooser() throws Exception {
        if (System.getSecurityManager() == null) {
            throw new RuntimeException("Security manager is not set!");
        }

        Robot robot = new Robot();
        robot.setAutoDelay(50);

        SwingUtilities.invokeLater(new Runnable() {

            public void run() {
                fileChooser = new JFileChooser();
                fileChooser.showOpenDialog(null);
                fileChooserIsShown = true;
                System.out.println("Start file chooser: " + fileChooserIsShown);
            }
        });

        long time = System.currentTimeMillis();
        while (fileChooser == null) {
            if (System.currentTimeMillis() - time >= 5000) {
                System.exit(1);
            }
            Thread.sleep(500);
        }

        Thread.sleep(500);
        robot.keyPress(KeyEvent.VK_ESCAPE);
        robot.keyRelease(KeyEvent.VK_ESCAPE);
        System.exit(0);
    }

    private static void generatePolicyFile() throws Exception {
        if (System.getSecurityManager() != null) {
            throw new RuntimeException("Security manager should be null!");
        }

        String osName = System.getProperty("os.name");
        System.out.println("os name: " + osName);
        if (!osName.startsWith("Windows")) {
            return;
        }
        System.out.println("after OS Check!");

        File defaultDirectory = FileSystemView.getFileSystemView().
                getDefaultDirectory();

        if (defaultDirectory == null) {
            throw new RuntimeException("Default directory is null!");
        }

        File policyFile = new File(POLICY_FILE);
        if (!policyFile.exists()) {
            policyFile.createNewFile();
        }

        PrintWriter writer = new PrintWriter(policyFile, "UTF-8");
        writer.println("grant {");
        String documents = defaultDirectory.getCanonicalPath();
        documents = documents.replace('\\', '/');
        // user.dir permission
        writer.print("  permission java.util.PropertyPermission");
        writer.print(" \"user.dir\",");
        writer.println(" \"read\";");
        // Documents permission
        writer.print("  permission java.io.FilePermission");
        writer.print(" \"" + documents + "\",");
        writer.println(" \"read\";");
        // Desktop permission
        writer.print("  permission java.io.FilePermission");
        writer.print(" \"" + documents.replace("Documents", "Desktop") + "\",");
        writer.println(" \"read\";");
        // robot permission // "java.awt.AWTPermission" "createRobot"
        writer.print("  permission java.awt.AWTPermission");
        writer.println(" \"createRobot\";");
        writer.println("};");
        writer.close();

        performTest();
    }

    private static void performTest() throws Exception {
        String javaPath = System.getProperty("java.home", "");
        String command = javaPath + File.separator + "bin" + File.separator + "java"
                + "  -Djava.security.manager -Djava.security.policy=" + POLICY_FILE
                + " bug8062561 CHECK_FILE_CHOOSER";
        System.out.println(command);
        int processExit = 0;

        Process process = Runtime.getRuntime().exec(command);

        try {
            processExit = process.waitFor();
        } catch (IllegalThreadStateException e) {
            throw new RuntimeException(e);
        }
        System.out.println("[RESULT] : "
                + "The sub process has cleanly exited : PASS");

        InputStream errorStream = process.getErrorStream();
        System.out.println("========= Child process stderr ========");
        boolean exception = dumpStream(errorStream);
        if (exception) {
            throw new RuntimeException("[RESULT] :"
                    + " Exception in child process : FAIL");
        }
        System.out.println("=======================================");

        InputStream processInputStream = process.getInputStream();
        System.out.println("========= Child process output ========");
        dumpStream(processInputStream);
        System.out.println("=======================================");

        if (processExit != 0) {
            System.out.println("process exit: " + processExit);
            process.destroy();
            throw new RuntimeException("[RESULT] : "
                    + "The sub process has not exited : FAIL");
        }
    }

    public static boolean dumpStream(InputStream in) throws IOException {
        String tempString;
        int count = in.available();
        boolean exception = false;
        while (count > 0) {
            byte[] b = new byte[count];
            in.read(b);
            tempString = new String(b);
            if (!exception) {
                exception = tempString.indexOf("Exception") != -1;
            }
            System.out.println(tempString);
            count = in.available();
        }

        return exception;
    }
}
