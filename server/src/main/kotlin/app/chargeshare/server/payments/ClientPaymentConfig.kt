package app.chargeshare.server.payments

import app.chargeshare.server.config.AppConfig
import app.chargeshare.shared.model.PaymentConfig
import app.chargeshare.shared.model.PaymentProviderKind

private const val CURRENCY = "eur"

/**
 * Baut die öffentliche Zahlungskonfiguration für `GET /api/v1/payments/config`.
 * Es werden ausschließlich Werte übernommen, die für Apps bestimmt sind (Publishable Key, Merchant-ID) –
 * `STRIPE_SECRET_KEY` und Webhook-Secret verlassen den Server nie.
 */
fun clientPaymentConfig(config: AppConfig, provider: PaymentProvider): PaymentConfig = PaymentConfig(
    provider = if (provider.autoConfirms) PaymentProviderKind.FAKE else PaymentProviderKind.STRIPE,
    publishableKey = config.stripePublishableKey,
    merchantDisplayName = config.merchantDisplayName,
    merchantCountryCode = config.merchantCountryCode,
    currency = CURRENCY,
    googlePayTestEnvironment = config.googlePayEnvironment != "production",
    applePayMerchantId = config.applePayMerchantId,
)
