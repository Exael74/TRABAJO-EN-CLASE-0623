package com.banco.auditoria;

import com.google.gson.Gson;

/** Audit consumer for Redis Streams — Stiven Esneider Pardo Gutierrez */
import com.google.gson.GsonBuilder;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.params.XReadGroupParams;
import redis.clients.jedis.resps.StreamEntry;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class AuditoriaConsumer {

    private static final String STREAM_KEY = "banco.transferencias";
    private static final String GROUP_NAME = "auditoria-group";
    private static final String CONSUMER_NAME = "auditoria-1";
    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 6379;
    private static final String LOG_FILE = "auditoria.log";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) {
        AuditoriaConsumer consumer = new AuditoriaConsumer();
        consumer.run();
    }

    /** Main execution loop with reconnection handling */

    public void run() {
        System.out.println("[" + now() + "] Starting audit consumer...");
        initLogFile();

        while (true) {
            try (Jedis jedis = new Jedis(REDIS_HOST, REDIS_PORT)) {
                System.out.println("[" + now() + "] Connected to Redis.");

                while (true) {
                    try {
                        List<Map.Entry<String, List<StreamEntry>>> results = jedis.xreadGroup(
                            GROUP_NAME,
                            CONSUMER_NAME,
                            XReadGroupParams.xReadGroupParams().block(0).count(1),
                            new AbstractMap.SimpleEntry<>(STREAM_KEY, ">")
                        );

                        if (results != null) {
                            for (Map.Entry<String, List<StreamEntry>> result : results) {
                                for (StreamEntry entry : result.getValue()) {
                                    processMessage(jedis, entry);
                                }
                            }
                        }
                    } catch (JedisConnectionException e) {
                        System.err.println("[" + now() + "] Connection error: " + e.getMessage());
                        break;
                    } catch (Exception e) {
                        System.err.println("[" + now() + "] Error processing message: " + e.getMessage());
                        e.printStackTrace();
                    }
                }

            } catch (Exception e) {
                System.err.println("[" + now() + "] Could not connect to Redis. Retrying in 5 seconds...");
            }

            sleep(5);
        }
    }

    private void processMessage(Jedis jedis, StreamEntry entry) {
        String messageId = entry.getID();
        Map<String, String> fields = entry.getFields();
        String jsonRaw = fields.get("evento");
        TransferenciaCreada evento = null;

        System.out.println("[" + now() + "] Mensaje recibido: ID=" + messageId);

        try {
            if (jsonRaw != null && !jsonRaw.trim().isEmpty()) {
                try {
                    evento = GSON.fromJson(jsonRaw, TransferenciaCreada.class);
                } catch (Exception e) {
                    System.out.println("[" + now() + "] Advertencia al parsear JSON anidado 'evento': " + e.getMessage());
                }
            }

            // Fallback: Parsear campos planos directamente de la entrada del stream (ej: Diapositiva 7)
            if (evento == null || evento.getEventId() == null) {
                String eventId = fields.get("eventId");
                if (eventId != null) {
                    String transferId = fields.get("transferId");
                    String from = fields.get("from");
                    String to = fields.get("to");
                    double amount = 0.0;
                    try {
                        String amountStr = fields.get("amount");
                        if (amountStr != null) {
                            amount = Double.parseDouble(amountStr);
                        }
                    } catch (NumberFormatException e) {
                        // Ignorar o registrar
                    }
                    String currency = fields.get("currency");
                    String createdAt = fields.get("createdAt");
                    
                    evento = new TransferenciaCreada(eventId, transferId, from, to, amount, currency, createdAt);
                    jsonRaw = GSON.toJson(evento); // Generar representación JSON para persistir
                }
            }

            if (evento == null || evento.getEventId() == null) {
                throw new RuntimeException("Estructura de evento nula o inválida (no se encontró JSON anidado 'evento' ni campos planos)");
            }

            persistEvent(jsonRaw, evento);

            jedis.xack(STREAM_KEY, GROUP_NAME, messageId);
            System.out.println("[" + now() + "] XACK enviado para " + messageId);

        } catch (Exception e) {
            System.err.println("[" + now() + "] Error al procesar " + messageId + ": " + e.getMessage());
            System.err.println("[" + now() + "] NO se hizo XACK. Mensaje quedará en PEL.");
        }
    }

    private void persistEvent(String jsonRaw, TransferenciaCreada evento) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(LOG_FILE, true))) {
            String linea = String.format(
                "[%s] eventId=%s | transferId=%s | from=%s | to=%s | amount=%.2f %s | createdAt=%s%n",
                now(),
                evento.getEventId(),
                evento.getTransferId(),
                evento.getFrom(),
                evento.getTo(),
                evento.getAmount(),
                evento.getCurrency() != null ? evento.getCurrency() : "N/A",
                evento.getCreatedAt() != null ? evento.getCreatedAt() : "N/A"
            );
            pw.write(linea);
            pw.write("  JSON: " + jsonRaw + System.lineSeparator());
            pw.write("---" + System.lineSeparator());
            System.out.println("[" + now() + "] Event persisted to " + LOG_FILE);
        } catch (IOException e) {
            throw new RuntimeException("Error writing to log file", e);
        }
    }

    private void initLogFile() {
        try {
            Path path = Paths.get(LOG_FILE);
            if (!Files.exists(path)) {
                Files.createFile(path);
                System.out.println("[" + now() + "] Log file " + LOG_FILE + " created.");
            }
        } catch (IOException e) {
            System.err.println("[" + now() + "] Could not create log file: " + e.getMessage());
        }
    }

    private String now() {
        return LocalDateTime.now().format(DTF);
    }

    private void sleep(int seconds) {
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
