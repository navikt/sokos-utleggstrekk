INSERT INTO fraskatt (id, trekkid, sekvensnummer, trekkversjon, opprettet, saksnummer, trekkpliktig, skyldner, trekkstatus, tidspunkt_opprettet) VALUES (1, 'trekk1', 1, 1, NOW(), 'sak1', '312978083', '12345678901', 'aktiv', NOW());
INSERT INTO fraskatt (id, trekkid, sekvensnummer, trekkversjon, opprettet, saksnummer, trekkpliktig, skyldner, trekkstatus, tidspunkt_opprettet) VALUES (2, 'trekk2', 2, 1, NOW(), 'sak1', '312978083', '12345678901', 'aktiv', NOW());
INSERT INTO fraskatt (id, trekkid, sekvensnummer, trekkversjon, opprettet, saksnummer, trekkpliktig, skyldner, trekkstatus, tidspunkt_opprettet) VALUES (3, 'trekk3', 3, 1, NOW(), 'sak1', '312978083', '12345678901', 'aktiv', NOW());
INSERT INTO fraskatt (id, trekkid, sekvensnummer, trekkversjon, opprettet, saksnummer, trekkpliktig, skyldner, trekkstatus, tidspunkt_opprettet) VALUES (4, 'trekk4', 4, 1, NOW(), 'sak1', '312978083', '12345678901', 'aktiv',NOW());
INSERT INTO fraskatt (id, trekkid, sekvensnummer, trekkversjon, opprettet, saksnummer, trekkpliktig, skyldner, trekkstatus, tidspunkt_opprettet) VALUES (5, 'trekk5', 5, 1, NOW(), 'sak1', '312978083', '12345678901', 'aktiv',NOW());

INSERT INTO betalingsinformasjonfraskatt (id, fraskatt_id, betalingsmottaker, kidnummer, kontonummer) VALUES (1, 1, '971648199', 'kidnr', '1234214');
INSERT INTO betalingsinformasjonfraskatt (id, fraskatt_id, betalingsmottaker, kidnummer, kontonummer) VALUES (2, 2, '971648199', 'kidnr', '1234214');
INSERT INTO betalingsinformasjonfraskatt (id, fraskatt_id, betalingsmottaker, kidnummer, kontonummer) VALUES (3, 3, '971648199', 'kidnr', '1234214');
INSERT INTO betalingsinformasjonfraskatt (id, fraskatt_id, betalingsmottaker, kidnummer, kontonummer) VALUES (4, 4, '971648199', 'kidnr', '1234214');
INSERT INTO betalingsinformasjonfraskatt (id, fraskatt_id, betalingsmottaker, kidnummer, kontonummer) VALUES (5, 5, '971648199', 'kidnr', '1234214');

INSERT INTO fraskatt_status (id, fraskatt_id, status, tidspunkt_satt) VALUES (1, 1, 'MOTTATT', NOW());
INSERT INTO fraskatt_status (id, fraskatt_id, status, tidspunkt_satt) VALUES (2, 2, 'BEHANDLET', NOW());
INSERT INTO fraskatt_status (id, fraskatt_id, status, tidspunkt_satt) VALUES (3, 3, 'AVVIST', NOW());
INSERT INTO fraskatt_status (id, fraskatt_id, status, tidspunkt_satt) VALUES (4, 4, 'REPETERES', NOW());
INSERT INTO fraskatt_status (id, fraskatt_id, status, tidspunkt_satt) VALUES (5, 5, 'HOPPES_OVER', NOW());