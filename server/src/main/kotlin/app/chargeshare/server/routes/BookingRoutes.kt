package app.chargeshare.server.routes

import app.chargeshare.server.Services
import app.chargeshare.server.payments.WebhookVerificationException
import app.chargeshare.server.plugins.JWT_AUTH
import app.chargeshare.server.util.badRequest
import app.chargeshare.server.util.validationError
import app.chargeshare.shared.model.BookingStatus
import app.chargeshare.shared.model.CancelBookingRequest
import app.chargeshare.shared.model.CreateBookingRequest
import app.chargeshare.shared.model.CreateReviewRequest
import app.chargeshare.shared.model.MessageResponse
import app.chargeshare.shared.model.OnboardingLink
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.bookingRoutes(s: Services) {
    authenticate(JWT_AUTH) {
        route("/bookings") {
            get {
                val p = call.page()
                val status = call.request.queryParameters["status"]?.takeIf { it.isNotEmpty() }?.let { v ->
                    BookingStatus.entries.firstOrNull { it.name.equals(v, ignoreCase = true) }
                        ?: throw validationError("status: must be one of pending confirmed active completed cancelled failed")
                }
                call.respond(s.bookings.list(call.userId(), status, p.limit, p.offset))
            }
            post { call.respond(HttpStatusCode.Created, s.bookings.create(call.userId(), call.receive<CreateBookingRequest>())) }
            get("/{id}") { call.respond(s.bookings.get(call.pathUuid("id", "booking id"), call.userId())) }
            delete("/{id}") {
                val id = call.pathUuid("id", "booking id")
                // Body ist optional (Go ignoriert Dekodierfehler)
                val reason = runCatching { call.receive<CancelBookingRequest>().reason }.getOrNull()
                s.bookings.cancel(id, call.userId(), reason)
                call.respond(HttpStatusCode.NoContent)
            }
            post("/{id}/pay") { call.respond(s.bookings.payPending(call.pathUuid("id", "booking id"), call.userId())) }
            post("/{id}/confirm-payment") {
                call.respond(s.bookings.confirmPayment(call.pathUuid("id", "booking id"), call.userId()))
            }
        }
        post("/reviews") {
            call.respond(HttpStatusCode.Created, s.reviews.create(call.userId(), call.receive<CreateReviewRequest>()))
        }
        route("/host") {
            get("/bookings") {
                val p = call.page()
                call.respond(s.host.bookings(call.userId(), p.limit, p.offset))
            }
            get("/earnings") {
                val q = call.request.queryParameters
                call.respond(s.host.earnings(call.userId(), q["from"], q["to"], q["tz"]))
            }
            get("/sessions") {
                val p = call.page()
                call.respond(s.host.sessions(call.userId(), p.limit, p.offset))
            }
        }
        route("/payments") {
            get("/config") { call.respond(s.paymentConfig) }
            post("/onboard") { call.respond(OnboardingLink(s.paymentsSvc.onboardingUrl(call.userId(), call.requestBaseUrl()))) }
            get("/payout-status") { call.respond(s.paymentsSvc.payoutStatus(call.userId(), refresh = false)) }
            post("/payout-status/refresh") { call.respond(s.paymentsSvc.payoutStatus(call.userId(), refresh = true)) }
            get("/onboard/return") {
                s.paymentsSvc.onboardingReturn(call.userId())
                call.respond(MessageResponse("onboarding complete"))
            }
        }
    }
    // Stripe-Webhook: oeffentlich, per Signatur gesichert
    post("/payments/webhook") {
        val payload = call.receiveText()
        if (payload.length > 65536) throw badRequest("could not read request body")
        val event = try {
            s.payments.parseWebhook(payload, call.request.header("Stripe-Signature"))
        } catch (e: WebhookVerificationException) {
            throw badRequest(e.message ?: "webhook verification failed")
        }
        s.bookings.applyPaymentEvent(event)
        call.respond(HttpStatusCode.OK)
    }
}
