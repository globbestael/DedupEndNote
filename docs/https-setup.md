# HTTPS for dedupendnote.nl (STRATO VPS with Plesk)

Runbook for issue #127. The interactive version of these steps is [`bin/https-wizard.sh`](../bin/https-wizard.sh), run on the server over SSH. The app itself needs no change: the browser opens its WebSocket with a relative URL (`dedup-utils.js`), and `RateLimitInterceptor` already trusts `X-Forwarded-For`.

## Design

```
Browser ──https:443──► Plesk nginx (Let's Encrypt) ──http──► 127.0.0.1:9777  (the app, unchanged)
Browser ──http:80────► Plesk nginx ──301──► https
Browser ──http:9777──► the app (kept for old links; decision on closing it is open)
```

Decisions (see the grilling on #127):

- **TLS terminates in Plesk** (Let's Encrypt + nginx reverse proxy). No second web server (Caddy/nginx) on a Plesk host: it would fight Plesk over ports 80/443.
- **No Spring Security**: it is for authentication, not transport encryption.
- **Names**: `dedupendnote.nl` only; there is no `www` DNS record. DNS stays at STRATO, not in Plesk.
- **Mail service is switched off** for the new Plesk domain, so Plesk does not take over mail routing.
- **HSTS is not enabled** yet (hard to undo). Add later with a short `max-age`.
- **Port 9777 stays open** for now. Close or bind to localhost in a separate decision after HTTPS has proven itself.
- **No iptables redirect**: an earlier `-t nat ... REDIRECT 80 -> 9777` rule is gone (empty nat table). Do not re-add it; it would block Plesk/Let's Encrypt on port 80.

## Steps (each has its undo in the wizard text)

1. **Inventory (read-only)**: `ss -tlnp`, `ufw status`, `iptables -t nat -S`, `ip6tables`, `/etc/iptables/rules.v4`. Stop if some foreign program holds :80/:443, or a persisted REDIRECT rule exists (no `rules.v4` at all is fine). Nothing listening on :80/:443 before a Plesk domain exists is normal.
2. **Backup**: Plesk's daily backup does not cover the app directory. `tar` it, plus `crontab -l`, `ufw`, `ss` output, into `$HOME`.
3. **DNS**: `dig +short dedupendnote.nl` must equal the VPS IP.
4. **Plesk: add domain** `dedupendnote.nl` (Blank website, no www, no DNS zone) and untick *Activate mail service*.
5. **Open ports 80/443**: `ufw allow 80/tcp`, `ufw allow 443/tcp` (only if `ufw` is active), and the Plesk Firewall extension if installed. STRATO's portal firewall already allows them. Verify from outside the server. Plesk starts nginx on 80/443 only once a domain is hosted, so this comes after the domain exists (without a hosted domain, nothing listens there).
6. **Plesk: Let's Encrypt** without www; tick the permanent HTTP→HTTPS redirect.
7. **Plesk: nginx proxy** (Apache & nginx Settings → Additional nginx directives, *Proxy mode* off):

   ```nginx
   location / {
       proxy_pass http://127.0.0.1:9777;
       proxy_http_version 1.1;
       proxy_set_header Host $host;
       proxy_set_header X-Real-IP $remote_addr;
       proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
       proxy_set_header X-Forwarded-Proto $scheme;
       proxy_set_header Upgrade $http_upgrade;
       proxy_set_header Connection "upgrade";
       client_max_body_size 150m;
       proxy_request_buffering off;
       proxy_read_timeout 1500s;
       proxy_send_timeout 1500s;
   }
   ```

   - `client_max_body_size 150m` matches `spring.servlet.multipart.max-file-size`; nginx's default of 1 MB would reject RIS uploads.
   - `1500s` exceeds `dedup.timeout-minutes` (20 min), so nginx never cuts a run off before the app does.
   - `Upgrade`/`Connection` carry the SockJS/STOMP WebSocket for progress messages.
   - If limits in `application.properties` change, change these two values with them.
8. **Verify**: `curl -I` on https (200), http (301 to https), `127.0.0.1:9777` (200), `/gs-guide-websocket/info` (JSON with `"websocket":true`); then a real RIS upload and run in the browser.

## Rollback

Empty *Additional nginx directives* and Apply (Plesk validates the config before applying; an invalid block is refused and the old config stays). The app on `:9777` is never touched, so the old URL works throughout. Restore the backup with `sudo tar xzf ~/dedupendnote-backup-*.tgz -C /` if the app directory itself was damaged.

## After it works

- Follow-up PR: use `https://dedupendnote.nl` in `src/main/cff/citation.cff` and `docs/Test_data.md`.
- `RateLimitInterceptor` takes the first `X-Forwarded-For` hop. While `:9777` is open to the internet a client can spoof it; closing or localhost-binding `:9777` (open decision) removes that.
