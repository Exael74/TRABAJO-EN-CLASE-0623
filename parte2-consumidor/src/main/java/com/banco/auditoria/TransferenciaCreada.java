package com.banco.auditoria;

import com.google.gson.annotations.SerializedName;

/** Transfer event POJO — Stiven Esneider Pardo Gutierrez */

public class TransferenciaCreada {

    @SerializedName("eventId")
    private String eventId;

    @SerializedName("transferId")
    private String transferId;

    @SerializedName("from")
    private String from;

    @SerializedName("to")
    private String to;

    @SerializedName("amount")
    private double amount;

    @SerializedName("currency")
    private String currency;

    @SerializedName("createdAt")
    private String createdAt;

    public TransferenciaCreada() {}

    public TransferenciaCreada(String eventId, String transferId, String from,
                               String to, double amount, String currency, String createdAt) {
        this.eventId = eventId;
        this.transferId = transferId;
        this.from = from;
        this.to = to;
        this.amount = amount;
        this.currency = currency;
        this.createdAt = createdAt;
    }

    public String getEventId() { return eventId; }
    public String getTransferId() { return transferId; }
    public String getFrom() { return from; }
    public String getTo() { return to; }
    public double getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getCreatedAt() { return createdAt; }
}
