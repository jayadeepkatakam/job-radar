// Finds every visible form field on the page and describes it so the agent can answer it.
// Each element gets a data-jr-id attribute so the Java side can locate it again.
// Returns: [{id, kind, label, required, maxLength, value, options: [{id, label}]}]
() => {
  const clean = (s) => (s || "").replace(/\s+/g, " ").replace(/\s*[*\u2731]\s*$/, "").trim();
  const visible = (el) => {
    const r = el.getBoundingClientRect();
    const cs = getComputedStyle(el);
    return r.width > 0 && r.height > 0 && cs.visibility !== "hidden" && cs.display !== "none";
  };
  let seq = 0;
  const tag = (el) => {
    if (!el.dataset.jrId) el.dataset.jrId = "jr-" + (++seq) + "-" + Math.random().toString(36).slice(2, 6);
    return el.dataset.jrId;
  };
  const textOf = (ids) => (ids || "").split(/\s+/).map((id) => document.getElementById(id))
      .filter(Boolean).map((n) => n.innerText || n.textContent).join(" ");

  // Text of a label element without the text of controls nested inside it.
  const labelText = (lab) => {
    const copy = lab.cloneNode(true);
    copy.querySelectorAll("input, select, textarea, [role=listbox], [role=option]").forEach((n) => n.remove());
    return copy.innerText || copy.textContent;
  };

  const labelFor = (el) => {
    if (el.labels && el.labels.length) return clean(labelText(el.labels[0]));
    if (el.getAttribute("aria-labelledby")) return clean(textOf(el.getAttribute("aria-labelledby")));
    if (el.getAttribute("aria-label")) return clean(el.getAttribute("aria-label"));
    let node = el.parentElement;
    for (let depth = 0; node && depth < 5; depth++, node = node.parentElement) {
      const lab = node.querySelector(":scope > label, :scope > legend, :scope > .label, :scope > [class*=label]");
      if (lab && !lab.contains(el)) return clean(labelText(lab));
    }
    return clean(el.getAttribute("placeholder") || el.getAttribute("name") || "");
  };

  // Raw question text for a radio/checkbox group (asterisk kept, so required-ness can be read).
  const squash = (t) => (t || "").replace(/\s+/g, " ").trim();
  const groupLabel = (el) => {
    const fs = el.closest("fieldset");
    if (fs) {
      const lg = fs.querySelector("legend");
      if (lg) return squash(lg.innerText);
    }
    const grp = el.closest("[role=radiogroup], [role=group]");
    if (grp) {
      if (grp.getAttribute("aria-labelledby")) return squash(textOf(grp.getAttribute("aria-labelledby")));
      if (grp.getAttribute("aria-label")) return squash(grp.getAttribute("aria-label"));
    }
    // Nearest container with a label-ish element that isn't one of the option labels.
    let node = el.parentElement;
    for (let depth = 0; node && depth < 6; depth++, node = node.parentElement) {
      const cands = node.querySelectorAll("label, legend, .label, [class*=label], [class*=question], h3, h4, p");
      for (const c of cands) {
        if (c.querySelector("input")) continue;
        if (c.htmlFor && document.getElementById(c.htmlFor)?.type === el.type) continue;
        const t = squash(c.innerText);
        if (t && clean(t).length > 2) return t;
      }
    }
    return "";
  };

  const isRequired = (el, label) =>
      !!(el.required || el.getAttribute("aria-required") === "true" || /[*\u2731]\s*$/.test(label || ""));

  const fields = [];
  const seenGroups = new Set();

  for (const el of document.querySelectorAll("input, select, textarea, [role=combobox], button[aria-haspopup=listbox]")) {
    const type = (el.getAttribute("type") || el.tagName).toLowerCase();
    if (["hidden", "submit", "button", "reset", "image", "search"].includes(type) && el.tagName !== "BUTTON") continue;
    if (el.disabled) continue;
    const rawLabel = el.labels && el.labels.length ? labelText(el.labels[0]) : "";

    if (type === "file") {
      fields.push({ id: tag(el), kind: "file", label: labelFor(el) || clean(el.getAttribute("name")),
                    required: isRequired(el, rawLabel), maxLength: null, value: "", options: [] });
      continue;
    }
    if (!visible(el) && !(type === "radio" || type === "checkbox")) continue;

    if (type === "radio" || type === "checkbox") {
      const name = el.name;
      const group = name ? [...document.querySelectorAll(`input[type=${type}][name="${CSS.escape(name)}"]`)] : [el];
      if (name && seenGroups.has(type + name)) continue;
      if (name) seenGroups.add(type + name);
      // The clickable thing may be the label (inputs are often visually hidden).
      if (!group.some((g) => visible(g) || (g.labels && g.labels[0] && visible(g.labels[0])))) continue;
      const options = group.map((g) => ({ id: tag(g), label: clean(g.labels && g.labels.length ? labelText(g.labels[0]) : g.value) }));
      if (type === "checkbox" && group.length === 1) {
        fields.push({ id: tag(el), kind: "checkbox", label: labelFor(el) || clean(groupLabel(el)), required: isRequired(el, rawLabel),
                      maxLength: null, value: el.checked ? "true" : "", options: [] });
      } else {
        const question = groupLabel(el);
        fields.push({ id: tag(el), kind: type === "radio" ? "radio" : "checkboxGroup", label: clean(question),
                      required: group.some((g) => g.required) || /[*\u2731]\s*$/.test(question),
                      maxLength: null, value: (group.find((g) => g.checked) || {}).value || "", options });
      }
      continue;
    }

    if (el.tagName === "SELECT") {
      const options = [...el.options].filter((o) => o.value !== "" && !/^(select|choose|please select|--)/i.test(o.text.trim()))
          .map((o) => ({ id: o.value, label: clean(o.text) }));
      fields.push({ id: tag(el), kind: "select", label: labelFor(el), required: isRequired(el, rawLabel),
                    maxLength: null, value: el.value, options });
      continue;
    }

    if (el.getAttribute("role") === "combobox" || el.getAttribute("aria-haspopup") === "listbox") {
      fields.push({ id: tag(el), kind: "combobox", label: labelFor(el), required: isRequired(el, rawLabel),
                    maxLength: null, value: clean(el.value || el.innerText || ""), options: [] });
      continue;
    }

    const kind = el.tagName === "TEXTAREA" ? "textarea" : (["email", "tel", "number", "url", "date"].includes(type) ? type : "text");
    fields.push({ id: tag(el), kind, label: labelFor(el), required: isRequired(el, rawLabel),
                  maxLength: el.maxLength > 0 ? el.maxLength : null, value: el.value || "", options: [] });
  }

  // Yes/No questions built from plain buttons (Ashby and some custom forms).
  for (const lab of document.querySelectorAll("label, legend, [class*=question], [class*=label]")) {
    const box = lab.parentElement;
    if (!box || box.querySelector("input:not([type=hidden]), select, textarea")) continue;
    const buttons = [...box.querySelectorAll("button[type=button]")].filter((b) => visible(b) && clean(b.innerText).length <= 20);
    if (buttons.length < 2 || buttons.length > 4) continue;
    if (buttons.some((b) => b.dataset.jrId)) continue;
    const label = clean(labelText(lab));
    if (!label) continue;
    fields.push({ id: tag(box), kind: "buttons", label, required: /\*\s*$/.test(lab.innerText), maxLength: null,
                  value: "", options: buttons.map((b) => ({ id: tag(b), label: clean(b.innerText) })) });
  }

  return fields;
}
