package com.minenorth_eurobank.api;

/** Résultat d'une tentative de paiement par carte. */
public enum PayResult {
    OK(""),
    INVALID_AMOUNT("Montant invalide."),
    NO_ACCOUNT("Vous n'avez pas de compte bancaire."),
    NO_CARD("Votre carte bancaire est requise."),
    FOREIGN_CARD("Cette carte ne vous appartient pas."),
    INSUFFICIENT_FUNDS("Solde insuffisant.");

    private final String message;

    PayResult(String message) {
        this.message = message;
    }

    /** Message prêt à afficher au joueur (vide si OK). */
    public String message() {
        return message;
    }
}
