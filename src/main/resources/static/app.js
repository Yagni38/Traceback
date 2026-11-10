
const API_URL = "/api/reports";
const CLAIMS_URL = "/api/claims";

const reportForm = document.getElementById("reportForm");
const itemsContainer = document.getElementById("itemsContainer");
const message = document.getElementById("message");

let allReports = [];

// ---------- Loading and showing reports ----------

// Load reports from Spring Boot
async function loadReports() {
    if (allReports.length === 0) {
        showLoading(itemsContainer, "Loading reports...");
    }

    try {
        const response = await fetch(API_URL);

        if (!response.ok) {
            throw new Error(await readError(response, "Unable to load reports"));
        }

        allReports = await response.json();

        updateStats();
        filterReports();

    } catch (error) {
        itemsContainer.replaceChildren();

        const errorBox = document.createElement("div");
        errorBox.className = "load-error";

        const text = document.createElement("p");
        text.textContent = "Could not load reports. Check that the server is running.";

        const retry = document.createElement("button");
        retry.className = "match-btn";
        retry.textContent = "Try Again";
        retry.addEventListener("click", loadReports);

        errorBox.append(text, retry);
        itemsContainer.appendChild(errorBox);
        console.error(error);
    }
}

// Update dashboard statistics
function updateStats() {
    document.getElementById("totalCount").textContent =
        allReports.length;

    document.getElementById("lostCount").textContent =
        allReports.filter(item => item.reportType === "LOST").length;

    document.getElementById("foundCount").textContent =
        allReports.filter(item => item.reportType === "FOUND").length;
}

// Display all reports
function displayReports(reports) {
    itemsContainer.replaceChildren();

    if (reports.length === 0) {
        const empty = document.createElement("p");
        empty.className = "no-match";
        empty.textContent = allReports.length === 0
            ? "No reports yet. Be the first to report an item."
            : "No reports match your search.";
        itemsContainer.appendChild(empty);
        return;
    }

    reports.forEach(item => {
        const card = document.createElement("div");
        card.className = "item-card";

        const type = document.createElement("span");
        type.className = "item-type " +
            (item.reportType === "LOST" ? "lost" : "found");
        type.textContent = item.reportType;

        const title = document.createElement("h3");
        title.textContent = item.title;

        const category = document.createElement("p");
        category.textContent = "Category: " + (item.category || "-");

        const color = document.createElement("p");
        color.textContent = "Color: " + (item.color || "-");

        const location = document.createElement("p");
        location.textContent = "Location: " + (item.location || "-");

        const description = document.createElement("p");
        description.textContent =
            "Description: " + (item.description || "-");

        const date = document.createElement("p");
        date.textContent = "Date: " + (item.eventDate || "-");

        // Status is shown to everyone; only the admin can change it
        const statusText = document.createElement("p");
        statusText.className = "report-status" +
            (item.status === "RETURNED" ? " returned" : "");
        statusText.textContent = item.status === "RETURNED"
            ? "Status: RETURNED to owner"
            : "Status: " + (item.status || "ACTIVE");

        const matchButton = document.createElement("button");
        matchButton.className = "match-btn";
        matchButton.textContent = "Find Matches";

        const matchContainer = document.createElement("div");
        matchContainer.className = "match-results";

        matchButton.addEventListener("click", () => {
            findMatches(item.id, matchContainer, matchButton);
        });

        card.append(
            type, title, category, color,
            location, description, date,
            statusText,
            matchButton, matchContainer
        );

        // Owners can claim active FOUND items
        addClaimForm(item, card);

        itemsContainer.appendChild(card);
    });
}

// ---------- Smart matching ----------

// Find matching reports
async function findMatches(reportId, container, button) {
    button.disabled = true;
    button.textContent = "Checking...";

    container.replaceChildren();

    try {
        const response = await fetch(`${API_URL}/${reportId}/matches`);

        if (!response.ok) {
            throw new Error(await readError(response, "Matching request failed"));
        }

        const matches = await response.json();

        if (matches.length === 0) {
            const noMatch = document.createElement("p");
            noMatch.className = "no-match";
            noMatch.textContent =
                "No possible matches found yet. Check again when more reports are added.";
            container.appendChild(noMatch);
            return;
        }

        const heading = document.createElement("h4");
        heading.textContent = "Possible Matches";
        container.appendChild(heading);

        matches.forEach(match => {
            container.appendChild(buildMatchCard(match));
        });

    } catch (error) {
        const errorMessage = document.createElement("p");
        errorMessage.className = "claim-message error";
        errorMessage.textContent =
            "Unable to find matches. Please try again.";
        container.appendChild(errorMessage);

        console.error(error);

    } finally {
        button.disabled = false;
        button.textContent = "Find Matches";
    }
}

function buildMatchCard(match) {
    const matchedItem = match.matchedItem;

    const matchCard = document.createElement("div");
    matchCard.className = "matched-item";

    const matchTitle = document.createElement("h4");
    matchTitle.textContent = matchedItem.title;

    const percentage = document.createElement("strong");
    percentage.className = "match-percentage";
    percentage.textContent = match.matchPercentage + "% Match";

    const location = document.createElement("p");
    location.textContent = "Location: " + (matchedItem.location || "-");

    const reasonsHeading = document.createElement("p");
    reasonsHeading.textContent = "Why this matched:";

    const reasonsList = document.createElement("ul");
    match.reasons.forEach(reason => {
        const li = document.createElement("li");
        li.textContent = reason;
        reasonsList.appendChild(li);
    });

    // Contact details (partly hidden for privacy unless an admin is logged in)
    const contact = document.createElement("div");
    contact.className = "contact-details";

    const contactHeading = document.createElement("p");
    contactHeading.className = "contact-heading";
    contactHeading.textContent =
        matchedItem.reportType === "FOUND"
            ? "Finder Contact Details"
            : "Owner Contact Details";

    const contactName = document.createElement("p");
    contactName.textContent =
        "Name: " + (matchedItem.reporterName || "Not provided");

    const contactEmail = document.createElement("p");
    contactEmail.textContent =
        "Email: " + (matchedItem.reporterEmail || "Not provided");

    const contactPhone = document.createElement("p");
    contactPhone.textContent =
        "Phone: " + (matchedItem.reporterPhone || "Not provided");

    contact.append(contactHeading, contactName, contactEmail, contactPhone);

    if (matchedItem.contactMasked) {
        const note = document.createElement("p");
        note.className = "privacy-note";
        note.textContent = matchedItem.reportType === "FOUND"
            ? "Contact details are partly hidden for privacy. Use \"Claim This Item\" on the found item and the TraceBack admin will verify your claim and connect you."
            : "Contact details are partly hidden for privacy. The TraceBack admin can connect you with the owner.";
        contact.appendChild(note);
    }

    matchCard.append(
        matchTitle,
        percentage,
        location,
        reasonsHeading,
        reasonsList,
        contact
    );

    return matchCard;
}

// ---------- Report form ----------

// Submit a new report
reportForm.addEventListener("submit", async function (event) {
    event.preventDefault();

    const submitButton = reportForm.querySelector(".submit-btn");
    clearFieldErrors(reportForm);
    setMessage(message, "", "");

    const report = {
        reportType: document.getElementById("reportType").value,
        title: document.getElementById("title").value.trim(),
        category: document.getElementById("category").value,
        color: document.getElementById("color").value.trim(),
        location: document.getElementById("location").value.trim(),
        description: document.getElementById("description").value.trim(),
        eventDate: document.getElementById("eventDate").value || null,
        reporterName: document.getElementById("reporterName").value.trim(),
        reporterEmail: document.getElementById("reporterEmail").value.trim(),
        reporterPhone: document.getElementById("reporterPhone").value.trim()
    };

    submitButton.disabled = true;
    submitButton.textContent = "Submitting...";

    try {
        const response = await fetch(API_URL, {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify(report)
        });

        if (!response.ok) {
            const error = await readErrorDetails(
                response, "Something went wrong. Please try again.");
            markFieldErrors(reportForm, error.fieldErrors);
            throw new Error(error.message);
        }

        setMessage(message,
            "Report submitted successfully! It now appears in Recent Reports below.",
            "success");

        reportForm.reset();

        await loadReports();

    } catch (error) {
        setMessage(message,
            error.message || "Something went wrong. Please try again.",
            "error");
        console.error(error);

    } finally {
        submitButton.disabled = false;
        submitButton.textContent = "Submit Report";
    }
});

// ---------- Search and filter ----------

function filterReports() {
    const search = document.getElementById("searchInput")
        .value.toLowerCase();

    const type = document.getElementById("filterType").value;

    const filtered = allReports.filter(item => {
        const matchesSearch =
            (item.title || "").toLowerCase().includes(search) ||
            (item.location || "").toLowerCase().includes(search) ||
            (item.description || "").toLowerCase().includes(search) ||
            (item.category || "").toLowerCase().includes(search);

        const matchesType =
            type === "ALL" || item.reportType === type;

        return matchesSearch && matchesType;
    });

    displayReports(filtered);
}

document.getElementById("searchInput")
    .addEventListener("input", filterReports);

document.getElementById("filterType")
    .addEventListener("change", filterReports);

// ---------- Claims ----------

function addClaimForm(item, parentElement) {

    // Claims are allowed only for active FOUND items
    if (
        !item ||
        item.reportType !== "FOUND" ||
        item.status !== "ACTIVE"
    ) {
        return;
    }

    const claimSection = document.createElement("div");
    claimSection.className = "claim-section";

    const claimButton = document.createElement("button");
    claimButton.textContent = "Claim This Item";
    claimButton.className = "claim-button";

    const form = document.createElement("form");
    form.className = "claim-form";
    form.style.display = "none";

    // Static template only: no user data is inserted here
    form.innerHTML = `
        <h3>Submit Your Claim</h3>

        <input type="text" name="claimantName"
               placeholder="Your Full Name" maxlength="80" required>

        <input type="email" name="claimantEmail"
               placeholder="Your Email" maxlength="120" required>

        <input type="tel" name="claimantPhone"
               placeholder="Your Phone Number (10 digits, optional)"
               pattern="[0-9]{10}" title="Enter a 10-digit phone number">

        <textarea name="claimReason"
                  placeholder="Describe something that proves this item belongs to you (at least 10 characters)"
                  minlength="10" maxlength="1000" required></textarea>

        <button type="submit" class="submit-claim-button">
            Submit Claim
        </button>

        <p class="claim-message" aria-live="polite"></p>
    `;

    claimButton.addEventListener("click", function () {
        form.style.display =
            form.style.display === "none" ? "block" : "none";
    });

    form.addEventListener("submit", async function (event) {
        event.preventDefault();

        const submitButton = form.querySelector(".submit-claim-button");
        const claimMessage = form.querySelector(".claim-message");

        clearFieldErrors(form);

        const formData = new FormData(form);

        const claimData = {
            reportId: item.id,
            claimantName: formData.get("claimantName").trim(),
            claimantEmail: formData.get("claimantEmail").trim(),
            claimantPhone: formData.get("claimantPhone").trim(),
            claimReason: formData.get("claimReason").trim()
        };

        submitButton.disabled = true;
        submitButton.textContent = "Submitting...";
        setMessage(claimMessage, "Submitting your claim...", "claim-message");

        try {
            const response = await fetch(CLAIMS_URL, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify(claimData)
            });

            if (!response.ok) {
                const error = await readErrorDetails(response, "Unable to submit claim");
                markFieldErrors(form, error.fieldErrors);
                throw new Error(error.message);
            }

            setMessage(claimMessage,
                "Claim submitted successfully! Status: PENDING. The admin will review it.",
                "claim-message success");

            form.reset();
            submitButton.textContent = "Submitted";
            claimButton.disabled = true;
            claimButton.textContent = "Claim Submitted";

        } catch (error) {
            setMessage(claimMessage, error.message, "claim-message error");
            submitButton.disabled = false;
            submitButton.textContent = "Submit Claim";
        }
    });

    claimSection.appendChild(claimButton);
    claimSection.appendChild(form);

    parentElement.appendChild(claimSection);
}

// ---------- Helpers ----------

function showLoading(container, text) {
    container.replaceChildren();
    const loading = document.createElement("p");
    loading.className = "loading";
    loading.textContent = text;
    container.appendChild(loading);
}

function setMessage(element, text, cssClass) {
    element.textContent = text;
    element.className = cssClass || "";
    element.style.color = "";
    if (element === message) {
        element.className = cssClass ? "form-message " + cssClass : "";
    }
}

function clearFieldErrors(form) {
    form.querySelectorAll(".input-error")
        .forEach(input => input.classList.remove("input-error"));
}

// Highlight inputs the server rejected (ids / names match the API field names)
function markFieldErrors(form, fieldErrors) {
    Object.keys(fieldErrors || {}).forEach(field => {
        const input = form.querySelector("#" + field + ", [name='" + field + "']");
        if (input) {
            input.classList.add("input-error");
        }
    });
}

// Read the error the API sends back: { message, fieldErrors }
async function readErrorDetails(response, fallback) {
    try {
        const data = await response.json();
        return {
            message: data.message || data.detail || data.error || fallback,
            fieldErrors: data.fieldErrors || {}
        };
    } catch (e) {
        return { message: fallback, fieldErrors: {} };
    }
}

async function readError(response, fallback) {
    return (await readErrorDetails(response, fallback)).message;
}

// Initial loading
loadReports();
