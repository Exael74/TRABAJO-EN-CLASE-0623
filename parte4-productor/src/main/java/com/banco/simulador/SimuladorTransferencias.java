package com.banco.simulador;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SimuladorTransferencias {

    private static final String STREAM_KEY = "banco.transferencias";
    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 6379;
    private static final long INTERVAL_SECONDS = 5;
    private static final List<String> ACCOUNTS = Arrays.asList(
        "cta-101", "cta-102", "cta-103", "cta-104", "cta-105",
        "cta-106", "cta-107", "cta-108", "cta-109", "cta-110"
    );
    private static final List<String> CURRENCIES = Arrays.asList("COP", "USD", "EUR");
    private static final Gson GSON = new GsonBuilder().create();
    private static final Random RANDOM = new Random();

    public static void main(String[] args) {
        SimuladorTransferencias simulador = new SimuladorTransferencias();
        simulador.run();
    }

    public void run() {
        System.out.println("[SIMULADOR] Starting transfer event generator...");

        while (true) {
            try (Jedis jedis = new Jedis(REDIS_HOST, REDIS_PORT)) {
                System.out.println("[SIMULADOR] Connected to Redis.");

                while (true) {
                    try {
                        TransferenciaCreada evento = generarEvento();
                        String json = GSON.toJson(evento);

                        String messageId = jedis.xadd(STREAM_KEY, null, "evento", json);
                        System.out.printf("[SIMULADOR] Published event: ID=%s | %s -> %s | %.2f %s%n",
                            messageId, evento.getFrom(), evento.getTo(),
                            evento.getAmount(), evento.getCurrency());

                        TimeUnit.SECONDS.sleep(INTERVAL_SECONDS);
                    } catch (JedisConnectionException e) {
                        System.err.println("[SIMULADOR] Connection lost: " + e.getMessage());
                        break;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            } catch (Exception e) {
                System.err.println("[SIMULADOR] Could not connect to Redis. Retrying in 5 seconds...");
            }

            sleep(5);
        }
    }

    private TransferenciaCreada generarEvento() {
        String eventId = "evt-" + UUID.randomUUID().toString().substring(0, 8);
        String transferId = "tr-" + UUID.randomUUID().toString().substring(0, 6);

        String from = ACCOUNTS.get(RANDOM.nextInt(ACCOUNTS.size()));
        String to;
        do {
            to = ACCOUNTS.get(RANDOM.nextInt(ACCOUNTS.size()));
        } while (to.equals(from));

        double amount = 100.0 + (RANDOM.nextDouble() * 9900.0);
        amount = Math.round(amount * 100.0) / 100.0;

        String currency = CURRENCIES.get(RANDOM.nextInt(CURRENCIES.size()));
        String createdAt = Instant.now().toString();

        return new TransferenciaCreada(eventId, transferId, from, to, amount, currency, createdAt);
    }

    private void sleep(int seconds) {
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
