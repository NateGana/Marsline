package com.marsline.eip;

import com.marsline.eip.task1.Task1MessageChannel;
import com.marsline.eip.task2.Task2ContentBasedRouter;
import com.marsline.eip.task3.Task3Aggregator;
import com.marsline.eip.task4.Task4MessageTranslator;
import com.marsline.eip.task5.Task5ErrorChannelRetry;

/**
 * Entry point for the MARSLINE EIP lab exam project.
 *
 * Run a specific task from Windows CMD with:
 *   mvn compile exec:java -Dexec.args="task1"
 *   mvn compile exec:java -Dexec.args="task2"
 *   mvn compile exec:java -Dexec.args="task3"
 *   mvn compile exec:java -Dexec.args="task4"
 *   mvn compile exec:java -Dexec.args="task5"
 *
 * With no argument, task1 runs by default.
 *
 * All five tasks (Message Channel, Content-Based Router, Aggregator,
 * Message Translator, Error Channel + Retry) are implemented in this
 * build.
 */
public class MarslineEipApplication {

    public static void main(String[] args) throws Exception {

        String task = args.length > 0 ? args[0] : "task1";

        System.out.println("ITP103 MIDTERM LAB EXAM | GROUP MARSLINE");
        System.out.println("Enterprise Integration Patterns - MARSLINE Busline (Cabuyao, Laguna)");
        System.out.println("Selected task: " + task);

        switch (task) {
            case "task1":
                Task1MessageChannel.run();
                break;

            case "task2":
                Task2ContentBasedRouter.run();
                break;

            case "task3":
                Task3Aggregator.run();
                break;

            case "task4":
                Task4MessageTranslator.run();
                break;

            case "task5":
                Task5ErrorChannelRetry.run();
                break;

            default:
                System.out.println();
                System.out.println("Unknown task: '" + task + "'");
                System.out.println("Available tasks: task1, task2, task3, task4, task5");
        }
    }
}
