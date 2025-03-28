/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package integralclient;
import java.io.*;
import java.net.*;
import java.util.Random;

public class IntegralClient {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 12345;
    private static final int NUM_THREADS = 10;
    private double totalResult = 0;
    private int completedThreads = 0;
    private PrintWriter out;
    private int tasksForThisClient;

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter outWriter = new PrintWriter(socket.getOutputStream(), true)) {

            IntegralClient client = new IntegralClient(outWriter);
            System.out.println("Подключено к серверу");

            Random rand = new Random();
            int clientId = rand.nextInt(1000);
            System.out.println("ID клиента: " + clientId);

            while (true) {
                String task = in.readLine();
                if (task == null) break;
                System.out.println("Получена задача: " + task);
                String[] parts = task.split(" ");
                double a = Double.parseDouble(parts[0]);
                double b = Double.parseDouble(parts[1]);
                double dx = Double.parseDouble(parts[2]);
                int numClients = Integer.parseInt(parts[3]);

                client.computeIntegral(a, b, dx, numClients, clientId);
            }
        } catch (IOException e) {
            System.err.println("Ошибка: " + e.getMessage());
        }
    }

    public IntegralClient(PrintWriter out) {
        this.out = out;
    }

    private void computeIntegral(double a, double b, double dx, int numClients, int clientId) {
        totalResult = 0;
        completedThreads = 0;

        double interval = (b - a) / NUM_THREADS;
        int tasksPerClient = NUM_THREADS / numClients;
        int extraTasks = NUM_THREADS % numClients;

        int startTask = clientId % numClients * tasksPerClient + Math.min(clientId % numClients, extraTasks);
        tasksForThisClient = tasksPerClient + (clientId % numClients < extraTasks ? 1 : 0);

        System.out.println("Клиент " + clientId + ": начальная задача=" + startTask + ", задач для клиента=" + tasksForThisClient);

        if (startTask >= NUM_THREADS) {
            System.out.println("Клиент " + clientId + ": задач не назначено");
            return;
        }

        double clientA = a + startTask * interval;
        double clientB = clientA + tasksForThisClient * interval;
        System.out.println("Клиент " + clientId + ": вычисляем от " + clientA + " до " + clientB);

        Thread[] threads = new Thread[tasksForThisClient];
        for (int i = 0; i < tasksForThisClient; i++) {
            double subA = clientA + i * interval;
            double subB = subA + interval;
            IntegralCalculator calculator = new IntegralCalculator(subA, subB, dx, this);
            threads[i] = new Thread(calculator);
            threads[i].start();
        }

        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("Клиент " + clientId + ": все потоки завершены");
    }

    private double trapezoidalIntegral(double a, double b, double dx) {
        int n = (int) Math.ceil((b - a) / dx);
        if (n <= 0) return 0;

        double sum = (function(a) + function(b)) / 2.0;
        for (int i = 1; i < n; i++) {
            double x = a + i * dx;
            sum += function(x);
        }
        return sum * dx;
    }

    private double function(double x) {
        return Math.exp(x) / x;
    }

    private synchronized void addPartialResult(double partialResult) {
        totalResult += partialResult;
        completedThreads++;
        System.out.println("Клиент: добавлен частичный результат, итог=" + totalResult + ", завершено=" + completedThreads + "/" + tasksForThisClient);
        if (completedThreads == tasksForThisClient) {
            out.println(totalResult);
            out.flush();
            System.out.println("Клиент: отправлен результат серверу: " + totalResult);
        }
    }

    class IntegralCalculator implements Runnable {
        private double a, b, step;
        private IntegralClient parent;

        public IntegralCalculator(double a, double b, double step, IntegralClient parent) {
            this.a = a;
            this.b = b;
            this.step = step;
            this.parent = parent;
        }

        @Override
        public void run() {
            double result = parent.trapezoidalIntegral(a, b, step);
            parent.addPartialResult(result);
        }
    }
}