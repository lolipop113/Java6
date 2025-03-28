/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */





package integralserver;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Scanner;

public class IntegralServer {
    private static final int PORT = 12345;
    private static ArrayList<ClientHandler> clients = new ArrayList<>();
    private static double totalResult = 0;
    private static int completedTasks = 0;
    private static final int NUM_SUBINTERVALS = 10;

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Сервер запущен на порту " + PORT);

        new Thread(() -> {
            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    ClientHandler client = new ClientHandler(clientSocket);
                    synchronized (clients) {
                        clients.add(client);
                    }
                    System.out.println("Подключен новый клиент: " + clientSocket.getInetAddress());
                    new Thread(client).start();
                } catch (IOException e) {
                    System.err.println("Ошибка подключения клиента: " + e.getMessage());
                }
            }
        }).start();

        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("Введите границы интеграла (a b) и шаг (dx):");
            String input = scanner.nextLine();
            String[] parts = input.split(" ");
            if (parts.length != 3) {
                System.out.println("Неверный формат ввода. Пример: 1 2 0.01");
                continue;
            }

            try {
                double a = Double.parseDouble(parts[0]);
                double b = Double.parseDouble(parts[1]);
                double dx = Double.parseDouble(parts[2]);

                if (a >= b || dx <= 0) {
                    System.out.println("Некорректные параметры: a должно быть меньше b, dx должен быть положительным");
                    continue;
                }

                synchronized (clients) {
                    if (clients.isEmpty()) {
                        System.out.println("Нет подключенных клиентов!");
                        continue;
                    }
                    distributeTask(a, b, dx);
                }
            } catch (NumberFormatException e) {
                System.out.println("Ошибка ввода: введите корректные числа!");
            }
        }
    }

    private static void distributeTask(double a, double b, double dx) throws IOException {
        totalResult = 0;
        completedTasks = 0;

        int numClients = clients.size();
        System.out.println("Распределяем задачу между " + numClients + " клиентами");

        for (ClientHandler client : clients) {
            client.sendTask(a, b, dx, numClients);
            System.out.println("Задача отправлена клиенту: " + a + " " + b + " " + dx + " " + numClients);
        }

        System.out.println("Ожидаем результаты...");
        synchronized (IntegralServer.class) {
            while (completedTasks < numClients) {
                try {
                    IntegralServer.class.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        System.out.println("Результат интеграла: " + totalResult);
    }

    private static synchronized void addPartialResult(double partialResult) {
        totalResult += partialResult;
        completedTasks++;
        System.out.println("Получен частичный результат: " + partialResult + ", завершено задач: " + completedTasks);
        if (completedTasks == clients.size()) {
            IntegralServer.class.notifyAll();
        }
    }

    static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;

        public ClientHandler(Socket socket) throws IOException {
            this.socket = socket;
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        }

        public void sendTask(double a, double b, double dx, int numClients) throws IOException {
            out.println(a + " " + b + " " + dx + " " + numClients);
            out.flush();
        }

        @Override
        public void run() {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    try {
                        double result = Double.parseDouble(line);
                        addPartialResult(result);
                    } catch (NumberFormatException e) {
                        System.err.println("Ошибка разбора результата от клиента: " + line);
                    }
                }
            } catch (IOException e) {
                System.err.println("Клиент отключился: " + e.getMessage());
            } finally {
                synchronized (clients) {
                    clients.remove(this);
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    System.err.println("Ошибка закрытия сокета: " + e.getMessage());
                }
            }
        }
    }
}