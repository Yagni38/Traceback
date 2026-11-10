
const API_URL = "/api/reports";
const CLAIMS_URL = "/api/claims";

const SESSION_MESSAGE =
    "Your admin login has expired. Please reload the page and log in again.";

let reports = [];
let claims = [];

async function loadAdminReports() {
    const container = document.getElementById("adminReports");

    try {
        const response = await fetch(API_URL);

        if (response.status === 401 || response.status === 403) {
            throw new Error(SESSION_MESSAGE);
        }

        if (!response.ok) {
            throw new Error("Could not load reports");
        }

        reports = await response.json();

        document.getElementById("totalCount").textContent =
            reports.length;

        document.getElementById("lostCount").textContent =
            reports.filter(r => r.reportType === "LOST").length;

        document.getElementById("foundCount").textContent =
            reports.filter(r => r.reportType === "FOUND").length;

        document.getElementById("returnedCount").textContent =
            reports.filter(r => r.status === "RETURNED").length;

        displayAdminReports();

    } catch (error) {
        container.textContent = error.message === SESSION_MESSAGE
            ? SESSION_MESSAGE
            : "Unable to load reports. Check that the server is running.";
        console.error(error);
    }
}

function displayAdminReports() {
    const container = document.getElementById("adminReports");
    const filter = document.getElementById("statusFilter").value;

    container.replaceChildren();

    const filtered = reports.filter(report => {
        if (filter === "ALL") return true;

        if (filter === "ACTIVE") return report.status === "ACTIVE";

        return report.reportType === filter &&
            report.status !== "RETURNED";
    });

    if (filtered.length === 0) {
        container.textContent = "No reports available.";
        return;
    }

    filtered.forEach(report => {
        const card = document.createElement("div");
        card.className = "admin-report";

        const title = document.createElement("h3");
        title.textContent = report.title;

        const type = document.createElement("p");
        type.textContent = "Type: " + report.reportType;

        const category = document.createElement("p");
        category.textContent = "Category: " + report.category;

        const location = document.createElement("p");
        location.textContent = "Location: " + report.location;

        const status = document.createElement("p");
        status.textContent = "Status: " + report.status;

        const reporter = document.createElement("p");
        reporter.textContent = "Reported by: " +
            (report.reporterName || "-") +
            (report.reporterEmail ? " (" + report.reporterEmail + ")" : "") +
            (report.reporterPhone ? " · " + report.reporterPhone : "");

        title.appendChild(makeBadge(report.status));

        card.append(title, type, category, location, status, reporter);

        if (report.status !== "RETURNED") {
            const button = document.createElement("button");
            button.textContent = "Mark as Returned";

            const message = document.createElement("p");
            message.className = "admin-message";

            button.addEventListener("click", () => {
                updateStatus(report, button, message);
            });

            card.append(button, message);
        }

        container.appendChild(card);
    });
}

async function updateStatus(report, button, message) {
    const pendingClaims = claims.filter(
        c => c.reportId === report.id && c.status === "PENDING").length;

    if (pendingClaims > 0 && !confirmReturn(button, pendingClaims)) {
        return;
    }

    button.disabled = true;
    button.textContent = "Updating...";
    message.textContent = "";
    message.className = "admin-message";

    try {
        const response = await fetch(`${API_URL}/${report.id}/status`, {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ status: "RETURNED" })
        });

        if (!response.ok) {
            throw new Error(await readError(response, "Unable to update item status."));
        }

        showFlash("\u201C" + report.title + "\u201D marked as returned.", "success");

        // Marking returned also rejects pending claims, so refresh both lists
        await loadAdminReports();
        await loadAdminClaims();

    } catch (error) {
        message.textContent = error.message;
        message.className = "admin-message error";
        button.disabled = false;
        button.textContent = "Mark as Returned";
        console.error(error);
    }
}

// First click asks for confirmation (no browser pop-up), second click confirms
function confirmReturn(button, pendingClaims) {
    if (button.dataset.confirm === "yes") {
        return true;
    }
    button.dataset.confirm = "yes";
    button.textContent = "Confirm: also rejects " + pendingClaims +
        " pending claim" + (pendingClaims > 1 ? "s" : "");
    button.classList.add("reject-btn");
    return false;
}

// ---------- Claims review ----------

async function loadAdminClaims() {
    const container = document.getElementById("adminClaims");

    try {
        const response = await fetch(CLAIMS_URL);

        if (response.status === 401 || response.status === 403) {
            throw new Error(SESSION_MESSAGE);
        }

        if (!response.ok) {
            throw new Error("Could not load claims");
        }

        claims = await response.json();

        document.getElementById("pendingClaimsCount").textContent =
            claims.filter(c => c.status === "PENDING").length;

        displayAdminClaims();

    } catch (error) {
        container.textContent = error.message === SESSION_MESSAGE
            ? SESSION_MESSAGE
            : "Unable to load claims.";
        console.error(error);
    }
}

function displayAdminClaims() {
    const container = document.getElementById("adminClaims");
    const filter = document.getElementById("claimFilter").value;

    container.replaceChildren();

    const filtered = claims
        .filter(c => filter === "ALL" || c.status === filter)
        .sort((a, b) => b.id - a.id);

    if (filtered.length === 0) {
        container.textContent = "No claims to show.";
        return;
    }

    filtered.forEach(claim => {
        const report = reports.find(r => r.id === claim.reportId);

        const card = document.createElement("div");
        card.className = "admin-report";

        const title = document.createElement("h3");
        title.textContent = "Claim for: " +
            (report ? report.title : "Report #" + claim.reportId);
        title.appendChild(makeBadge(claim.status));

        const itemInfo = document.createElement("p");
        itemInfo.textContent = report
            ? "Item: " + (report.category || "-") + " · " +
              (report.color || "-") + " · found at " +
              (report.location || "-") + " (item " + report.status + ")"
            : "Item details unavailable";

        const claimant = document.createElement("p");
        claimant.textContent = "Claimant: " + claim.claimantName;

        const contact = document.createElement("p");
        contact.textContent = "Contact: " + claim.claimantEmail +
            (claim.claimantPhone ? " · " + claim.claimantPhone : "");

        const submitted = document.createElement("p");
        submitted.textContent = "Submitted: " + formatDate(claim.createdAt);

        const reason = document.createElement("p");
        reason.className = "claim-reason";
        reason.textContent = claim.claimReason;

        card.append(title, itemInfo, claimant, contact, submitted, reason);

        if (claim.status === "PENDING") {
            const actions = document.createElement("div");
            actions.className = "claim-actions";

            const approve = document.createElement("button");
            approve.className = "approve-btn";
            approve.textContent = "Approve & Mark Returned";

            const reject = document.createElement("button");
            reject.className = "reject-btn";
            reject.textContent = "Reject";

            const message = document.createElement("p");
            message.className = "admin-message";

            approve.addEventListener("click", () =>
                reviewClaim(claim.id, "APPROVED", [approve, reject], message));
            reject.addEventListener("click", () =>
                reviewClaim(claim.id, "REJECTED", [approve, reject], message));

            actions.append(approve, reject);
            card.append(actions, message);
        }

        container.appendChild(card);
    });
}

async function reviewClaim(id, status, buttons, message) {
    buttons.forEach(b => b.disabled = true);
    const clicked = status === "APPROVED" ? buttons[0] : buttons[1];
    const originalText = clicked.textContent;
    clicked.textContent = status === "APPROVED" ? "Approving..." : "Rejecting...";
    message.textContent = "";
    message.className = "admin-message";

    try {
        const response = await fetch(`${CLAIMS_URL}/${id}/status`, {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ status })
        });

        if (!response.ok) {
            throw new Error(await readError(response, "Unable to update claim."));
        }

        showFlash(status === "APPROVED"
            ? "Claim approved. The item is now marked as returned."
            : "Claim rejected.", "success");

        // Approving changes the item status too, so refresh both lists
        await loadAdminReports();
        await loadAdminClaims();

    } catch (error) {
        message.textContent = error.message;
        message.className = "admin-message error";
        buttons.forEach(b => b.disabled = false);
        clicked.textContent = originalText;
        console.error(error);
    }
}

// ---------- Helpers ----------

// Short success/error banner at the top of the page
let flashTimer;
function showFlash(text, type) {
    const flash = document.getElementById("adminFlash");
    flash.textContent = text;
    flash.className = "admin-flash " + type;
    clearTimeout(flashTimer);
    flashTimer = setTimeout(() => {
        flash.className = "admin-flash";
        flash.textContent = "";
    }, 5000);
}

async function readError(response, fallback) {
    if (response.status === 401 || response.status === 403) {
        return SESSION_MESSAGE;
    }
    try {
        const data = await response.json();
        return data.message || data.error || fallback;
    } catch (e) {
        return fallback;
    }
}

function makeBadge(status) {
    const badge = document.createElement("span");
    badge.className = "badge " + String(status || "").toLowerCase();
    badge.textContent = status || "-";
    return badge;
}

function formatDate(value) {
    if (!value) return "-";
    const date = new Date(value);
    return isNaN(date) ? value : date.toLocaleString();
}

document.getElementById("statusFilter")
    .addEventListener("change", displayAdminReports);

document.getElementById("claimFilter")
    .addEventListener("change", displayAdminClaims);

// Reports first, so claims can show the item names
loadAdminReports().then(loadAdminClaims);
