/*
 * Footer contact link, shared by every page that renders a footer.
 *
 * The address arrives as two halves in window.WFPS_DATA and is joined here, so the
 * complete address never appears as a contiguous string in the served HTML.
 */
(function () {
    'use strict';

    const target = document.getElementById('contact-link');
    const user = (window.WFPS_DATA && window.WFPS_DATA.contactUser) || '';
    const domain = (window.WFPS_DATA && window.WFPS_DATA.contactDomain) || '';
    if (!target || !user || !domain) {
        return;
    }

    const link = document.createElement('a');
    link.href = 'mailto:' + user + '@' + domain;
    link.textContent = user + '@' + domain;
    target.appendChild(link);
})();
