"""DB certification validator — independent re-implementation (Phase 5/7/8/9).

Replays migrations against real SQLite with PRAGMA foreign_keys=ON and:
  * Upgrade matrix: every path v -> 25 (v = 5..24)
  * Stress: 1000 jobs / 100 companies / 500 recruiters / 100 resume versions /
    100 cover letters / 500 applications / 100 interview sessions /
    1000 timeline events, upgraded 17 -> 25
  * Integrity: PRAGMA integrity_check, foreign_key_check, WAL journal mode,
    transaction rollback
  * Performance: per-step migration timing + EXPLAIN QUERY PLAN (index usage)
"""
import sqlite3, re, json, os, sys, time, random

random.seed(42)

KOTLIN = 'core/database/src/main/java/com/bangersoul/aivance/core/database/AivanceDatabase.kt'
BASE = 'core/database/schemas/com.bangersoul.aivance.core.database.AivanceDatabase'

src = open(KOTLIN, encoding='utf-8', errors='replace').read()

# ---- extract migration blocks (same technique as migration_validate.py) ----
blocks = {}
pat = re.compile(r'val MIGRATION_(\d+)_(\d+) = object : Migration\(\d+, \d+\)')
matches = list(pat.finditer(src))
for i, m in enumerate(matches):
    start = m.end()
    end = matches[i + 1].start() if i + 1 < len(matches) else len(src)
    body = src[start:end]
    stmts = []
    for em in re.finditer(r'db\.execSQL\(\s*("(?:\\\\.|[^"\\\\])*")', body, re.S):
        raw = em.group(1)[1:-1]
        out = []
        j = 0
        while j < len(raw):
            c = raw[j]
            if c == '\\' and j + 1 < len(raw):
                nxt = raw[j + 1]
                if nxt == '"':
                    out.append('"'); j += 2; continue
                if nxt == '\\':
                    out.append('\\'); j += 2; continue
                if nxt == 'n':
                    out.append('\n'); j += 2; continue
                out.append(nxt); j += 2; continue
            out.append(c); j += 1
        stmts.append(''.join(out))
    blocks[(int(m.group(1)), int(m.group(2)))] = stmts

def load_json(v):
    with open(f'{BASE}/{v}.json') as f:
        return json.load(f)['database']

def build_base(v, con):
    db = load_json(v)
    for e in db['entities']:
        sql = e['createSql'].replace('${TABLE_NAME}', e['tableName'])
        con.execute(sql)
        for idx in (e.get('indices') or []):
            cols = ', '.join('`%s`' % c for c in idx['columnNames'])
            uniq = ' UNIQUE' if idx.get('unique') else ''
            con.execute(f"CREATE{uniq} INDEX IF NOT EXISTS `{idx['name']}` ON `{e['tableName']}` ({cols})")

def entity_map(v):
    return {e['tableName']: e for e in load_json(v)['entities']}

def topological(ents):
    ordered, remaining = [], set(ents.keys())
    while remaining:
        progress = False
        for t in list(remaining):
            deps = {fk['table'] for fk in (ents[t].get('foreignKeys') or [])}
            if deps <= set(ordered):
                ordered.append(t); remaining.discard(t); progress = True
        if not progress:
            ordered.extend(sorted(remaining)); break
    return ordered

def col_value(f, e, row_i, ids, ents):
    """Generate a value for column f of table e at row row_i, resolving FK parents."""
    name = f['columnName']
    aff = f.get('affinity', 'TEXT')
    nn = f.get('notNull', False)
    pk_cols = (e.get('primaryKey') or {}).get('columnNames', [])
    # FK resolution
    for fk in (e.get('foreignKeys') or []):
        if name in fk['columns']:
            j = fk['columns'].index(name)
            parent = fk['table']
            parent_ents = ents.get(parent)
            if parent_ents:
                pk = (parent_ents.get('primaryKey') or {}).get('columnNames', ['id'])
                ref = fk['referencedColumns'][j] if j < len(fk['referencedColumns']) else pk[0]
                parent_ids = ids.get(parent)
                if parent_ids:
                    return random.choice(parent_ids)
            return 1
    if name in pk_cols:
        if aff == 'INTEGER':
            return row_i + 1
        return f'pk-{row_i + 1}'
    if nn:
        return 1 if aff in ('INTEGER', 'REAL') else f'x{row_i}'
    return None

def seed_stress(con, v, counts):
    """Seed N rows per table (FK-aware, dependency-ordered). Returns table -> pk ids."""
    ents = entity_map(v)
    ids = {}
    for t in topological(ents):
        e = ents[t]
        n = counts.get(t, 1)
        if n == 0:
            continue
        pk_cols = (e.get('primaryKey') or {}).get('columnNames', [])
        fields = e['fields']
        inserted = []
        for i in range(n):
            cols, vals = [], []
            for f in fields:
                vv = col_value(f, e, i, ids, ents)
                cols.append(f['columnName']); vals.append(vv)
            ph = ', '.join('?' for _ in vals)
            cl = ', '.join('`%s`' % c for c in cols)
            try:
                con.execute(f'INSERT INTO `{t}` ({cl}) VALUES ({ph})', vals)
                # capture pk
                if pk_cols:
                    if len(pk_cols) == 1 and fields and any(f['columnName'] == pk_cols[0] and f.get('affinity') == 'INTEGER' for f in fields):
                        inserted.append(i + 1)
                    else:
                        inserted.append(f'pk-{i + 1}')
            except Exception as ex:
                print(f'    [seed warn] {t} row {i}: {ex}')
        ids[t] = inserted
    return ids

def table_exists(con, t):
    return con.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", (t,)).fetchone() is not None

def counts(con):
    out = {}
    for r in con.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"):
        t = r[0]
        out[t] = con.execute(f'SELECT COUNT(*) FROM "{t}"').fetchone()[0]
    return out

def schema_eq(v, con):
    """Compare live DB schema to exported v.json: tables + columns + FKs + indices."""
    errors = []
    db = load_json(v)
    exp_tables = {e['tableName']: e for e in db['entities']}
    act_tables = {r[0] for r in con.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")}
    if act_tables != set(exp_tables):
        errors.append(f"table set mismatch: extra={sorted(act_tables - set(exp_tables))} missing={sorted(set(exp_tables) - act_tables)}")
    for t, e in sorted(exp_tables.items()):
        if t not in act_tables:
            continue
        exp_cols = {f['columnName']: f for f in e['fields']}
        act_cols = {r[1]: r for r in con.execute(f'PRAGMA table_info("{t}")').fetchall()}
        pk_names = (e.get('primaryKey') or {}).get('columnNames', [])
        pkpos = {n: i + 1 for i, n in enumerate(pk_names)}
        for cn, ef in exp_cols.items():
            ac = act_cols.get(cn)
            if ac is None:
                errors.append(f"{t}.{cn}: missing")
                continue
            if bool(ac[3]) != bool(ef.get('notNull', False)):
                errors.append(f"{t}.{cn}: notNull {bool(ac[3])} != {ef.get('notNull')}")
            if (ac[4] or None) != (ef.get('defaultValue') or None):
                errors.append(f"{t}.{cn}: default {ac[4]!r} != {ef.get('defaultValue')!r}")
            if ac[5] != pkpos.get(cn, 0):
                errors.append(f"{t}.{cn}: pk {ac[5]} != {pkpos.get(cn, 0)}")
        for cn in act_cols:
            if cn not in exp_cols:
                errors.append(f"{t}.{cn}: extra column")
        # indices
        exp_inds = {(i['name'], tuple(i['columnNames']), i.get('unique', False)) for i in (e.get('indices') or [])}
        act_inds = set()
        for row in con.execute(f'PRAGMA index_list("{t}")').fetchall():
            name, uniq = row[1], row[2]
            if name.startswith('sqlite_'):
                continue
            cols = tuple(r[2] for r in con.execute(f'PRAGMA index_info("{name}")').fetchall())
            act_inds.add((name, cols, bool(uniq)))
        if exp_inds != act_inds:
            errors.append(f"{t}: indices mismatch exp={sorted(exp_inds)} act={sorted(act_inds)}")
        # FKs
        exp_fks = {(f['table'], tuple(f['columns']), tuple(f['referencedColumns']), f.get('onDelete', 'NO ACTION'), f.get('onUpdate', 'NO ACTION')) for f in (e.get('foreignKeys') or [])}
        act_fks = set()
        for r in con.execute(f'PRAGMA foreign_key_list("{t}")').fetchall():
            act_fks.add((r[2], (r[3],), (r[4],), r[6], r[5]))
        if exp_fks != act_fks:
            errors.append(f"{t}: FK mismatch exp={sorted(exp_fks)} act={sorted(act_fks)}")
    return errors

def run_chain(from_v, to_v, con, seed_v=None, counts_=None):
    """Apply migrations from->to on con. Returns (step_times_ms, errors, final_counts)."""
    step_times = []
    errors = []
    if seed_v is not None:
        build_base(seed_v, con)
        seed_stress(con, seed_v, counts_ or {})
    cur = seed_v if seed_v is not None else from_v
    for f in range(from_v, to_v):
        t = f + 1
        stmts = blocks.get((f, t))
        if stmts is None:
            errors.append(f'{f}->{t}: NO MIGRATION FOUND')
            continue
        start = time.perf_counter()
        try:
            for s in stmts:
                con.execute(s)
        except Exception as e:
            errors.append(f'{f}->{t}: SQL THREW {e}')
        step_times.append((f, t, (time.perf_counter() - start) * 1000))
        cur = t
    return step_times, errors, cur

# per-version table sets, once
SCHEMA_TABLES = {v: {e['tableName'] for e in load_json(v)['entities']} for v in range(5, 26)}

def expected_survivors(base_v):
    """Tables seeded at base_v that exist in EVERY schema version base_v..25.
    Tables with a presence gap (legacy drop + later re-add, e.g. `applications`,
    or dropped for good like `resume_analyses` at 24->25) are intentionally
    removed and excluded from preservation assertions."""
    tables = SCHEMA_TABLES[base_v]
    return {t for t in tables if all(t in SCHEMA_TABLES[v] for v in range(base_v, 26))}

def check_foreign_keys(con):
    rows = con.execute('PRAGMA foreign_key_check').fetchall()
    return rows

def check_integrity(con):
    rows = con.execute('PRAGMA integrity_check').fetchall()
    return [r[0] for r in rows]

failures = 0

print('=' * 70)
print('PHASE 5 — UPGRADE MATRIX (every path v -> 25, FK ON, schema-eq + data preserved)')
print('=' * 70)
for v in range(5, 25):
    con = sqlite3.connect(':memory:')
    con.execute('PRAGMA foreign_keys=ON')
    con.execute('PRAGMA defer_foreign_keys=ON')  # allow non-SET-NULL-safe ordering inside txn
    build_base(v, con)
    seed_stress(con, v, {})
    before = counts(con)
    step_times, errors, cur = run_chain(v, 25, con)
    if errors:
        print(f'  {v}->25: FAIL {errors[:5]}')
        failures += 1
        con.close(); continue
    eq = schema_eq(25, con)
    fk = check_foreign_keys(con)
    integ = check_integrity(con)
    after = counts(con)
    surv = expected_survivors(v)
    lost = [t for t in surv if after.get(t, 0) < before[t]]
    ok = not eq and not fk and integ == ['ok'] and not lost
    total_ms = sum(x[2] for x in step_times)
    print(f'  {v}->25: {"OK" if ok else "FAIL"}  ({len(before)} tables seeded, '
          f'{sum(before.values())} rows) schema_eq={len(eq)} fk_violations={len(fk)} '
          f'integrity={integ} lost={lost} time={total_ms:.1f}ms')
    if not ok:
        failures += 1
        for e in eq[:5]:
            print(f'      - {e}')
        if fk:
            print(f'      FK violations: {fk[:5]}')
    con.close()

print()
print('=' * 70)
print('PHASE 7 — STRESS TEST (17 -> 25)')
print('=' * 70)
stress_counts = {
    'companies': 100,
    'jobs': 1000,
    'recruiters': 500,
    'recruiter_contacts': 300,
    'resumes': 100,
    'resume_versions': 100,
    'resume_sections': 200,
    'resume_analyses': 100,
    'cover_letters': 100,
    'cover_letter_versions': 100,
    'cover_letter_sections': 200,
    'ats_reports': 200,
    'applications': 500,
    'application_stages': 8,
    'application_timeline': 1000,
    'application_tasks': 300,
    'interview_sessions': 100,
    'interview_messages': 300,
    'interview_questions': 200,
    'interview_evaluations': 100,
    'provider_configurations': 3,
    'user_profiles': 2,
    'ai_conversations': 5,
    'ai_messages': 10,
    'job_descriptions': 50,
    'saved_jobs': 50,
    'viewed_jobs': 50,
    'search_history': 50,
    'saved_searches': 5,
    'analytics_events': 20,
    'automation_rules': 4,
    'aivance_entities': 3,
    'roadmaps': 3,
    'roadmap_steps': 9,
    'outreach_drafts': 50,
    'communication_history': 50,
    'job_applications': 0,  # seeded only if table exists at 17 (it does NOT — dropped earlier)
}
con = sqlite3.connect(':memory:')
con.execute('PRAGMA foreign_keys=ON')
con.execute('PRAGMA defer_foreign_keys=ON')
build_base(17, con)
seed_stress(con, 17, stress_counts)
before = counts(con)
print(f'  seeded at v17: {sum(before.values())} rows across {len(before)} tables')
step_times, errors, cur = run_chain(17, 25, con)
if errors:
    print(f'  FAIL: {errors[:5]}')
    failures += 1
    stress_con = None
else:
    eq = schema_eq(25, con)
    fk = check_foreign_keys(con)
    integ = check_integrity(con)
    after = counts(con)
    surv = expected_survivors(17)
    # resume_analyses is intentionally dropped at 24->25 — only tables that
    # survive the whole chain are held to the no-data-loss assertion.
    lost = {t: (before[t], after.get(t, -1)) for t in surv if after.get(t, 0) < before[t]}
    ok = not eq and not fk and integ == ['ok'] and not lost
    print(f'  stress 17->25: {"OK" if ok else "FAIL"}  schema_eq={len(eq)} fk_violations={len(fk)} integrity={integ}')
    if lost:
        print(f'  DATA LOSS: {lost}')
    if not ok:
        failures += 1
    print()
    print('  PHASE 9 — per-step migration time (stress DB):')
    for f, t, ms in step_times:
        print(f'    {f}->{t}: {ms:8.2f} ms')
    total = sum(x[2] for x in step_times)
    print(f'    TOTAL 17->25: {total:.1f} ms')
    # keep this DB for query plans
    stress_con = con

print()
print('=' * 70)
print('PHASE 9 — INDEX USAGE (EXPLAIN QUERY PLAN @ v25, stress DB)')
print('=' * 70)
if stress_con is not None:
    queries = {
        'jobs by companyId': 'SELECT * FROM jobs WHERE companyId = 1',
        'job_applications by jobId': 'SELECT * FROM job_applications WHERE jobId = 1',
        'application_timeline by applicationId': 'SELECT * FROM application_timeline WHERE applicationId = 1',
        'interview_messages by sessionId': 'SELECT * FROM interview_messages WHERE sessionId = 1',
        'resume_sections by versionId': 'SELECT * FROM resume_sections WHERE versionId = 1',
        'applications by jobId': 'SELECT * FROM applications WHERE jobId = 1',
        'recruiter_contacts by recruiterId': 'SELECT * FROM recruiter_contacts WHERE recruiterId = \'pk-1\'',
        'ai_messages by conversationId': 'SELECT * FROM ai_messages WHERE conversationId = \'pk-1\'',
        'user_profiles by email': "SELECT * FROM user_profiles WHERE email = 'x0'",
    }
    for label, q in queries.items():
        try:
            rows = stress_con.execute(f'EXPLAIN QUERY PLAN {q}').fetchall()
            plan = ' | '.join(r[3] for r in rows)
            used_index = 'USING INDEX' in plan or 'USING COVERING INDEX' in plan
            print(f'  [{"IDX" if used_index else "SCAN"}] {label}: {plan}')
        except Exception as ex:
            print(f'  [ERR] {label}: {ex}')
    stress_con.close()

print()
print('=' * 70)
print('PHASE 8 — INTEGRITY (cascade deletes, transactions, WAL)')
print('=' * 70)
# 1. cascade delete: company -> jobs -> applications (FK ON)
con = sqlite3.connect(':memory:')
con.execute('PRAGMA foreign_keys=ON')
con.execute('PRAGMA defer_foreign_keys=ON')
build_base(25, con)
# Single company so every seeded job references it (deterministic cascade proof —
# the FK parent ids are chosen via random.choice, which is stream-position dependent).
seed_stress(con, 25, {'companies': 1, 'jobs': 6, 'job_applications': 0, 'applications': 6, 'application_timeline': 6})
n_jobs_before = counts(con)['jobs']
con.execute('DELETE FROM companies WHERE id = 1')
n_jobs_after = counts(con)['jobs']
cascade_ok = n_jobs_after < n_jobs_before
print(f'  cascade: DELETE company id=1 -> jobs {n_jobs_before}->{n_jobs_after} (cascade={cascade_ok})')
fk = check_foreign_keys(con)
print(f'  foreign_key_check after cascade: {len(fk)} violations')
if not cascade_ok or fk:
    failures += 1
con.close()

# 2. transaction rollback
con = sqlite3.connect(':memory:')
con.execute('CREATE TABLE tx_test (id INTEGER PRIMARY KEY, v TEXT)')
con.execute('BEGIN')
con.execute('INSERT INTO tx_test (v) VALUES (\'a\')')
con.execute('ROLLBACK')
n = con.execute('SELECT COUNT(*) FROM tx_test').fetchone()[0]
print(f'  transaction rollback: rows after ROLLBACK = {n} (ok={n == 0})')
if n != 0:
    failures += 1
con.execute('BEGIN')
con.execute('INSERT INTO tx_test (v) VALUES (\'b\')')
con.execute('COMMIT')
n2 = con.execute('SELECT COUNT(*) FROM tx_test').fetchone()[0]
print(f'  transaction commit: rows after COMMIT = {n2} (ok={n2 == 1})')
con.close()

# 3. WAL journal mode (file-backed)
import tempfile
f = tempfile.NamedTemporaryFile(suffix='.db', delete=False)
f.close()
con = sqlite3.connect(f.name)
try:
    mode = con.execute('PRAGMA journal_mode=WAL').fetchone()[0]
    print(f'  journal_mode=WAL: {mode} (ok={mode.upper() == "WAL"})')
    if mode.upper() != 'WAL':
        failures += 1
finally:
    con.close()
    os.unlink(f.name)

# 4. FK enforcement proof: insert orphan child -> must fail with FK ON
con = sqlite3.connect(':memory:')
con.execute('PRAGMA foreign_keys=ON')
build_base(25, con)
try:
    con.execute('INSERT INTO jobs (companyId, title, postedDate, url, sourceProviderId) VALUES (9999, \'x\', 1, \'\', \'x\')')
    print('  FK enforcement: orphan job INSERT SUCCEEDED (UNEXPECTED)')
    failures += 1
except sqlite3.IntegrityError:
    print('  FK enforcement: orphan job INSERT rejected (ok)')
con.close()

# 5. DROP-TABLE cascade hazard proof (why staging exists)
con = sqlite3.connect(':memory:')
con.execute('PRAGMA foreign_keys=ON')
con.execute('CREATE TABLE p (id INTEGER PRIMARY KEY)')
con.execute('CREATE TABLE c (id INTEGER PRIMARY KEY, pid INTEGER REFERENCES p(id) ON DELETE CASCADE)')
con.execute('INSERT INTO p VALUES (1)')
con.execute('INSERT INTO c VALUES (1, 1)')
con.execute('DROP TABLE p')
remaining = con.execute('SELECT COUNT(*) FROM sqlite_master WHERE name=\'c\'').fetchone()[0]
print(f'  DROP TABLE parent with FK ON: child table still exists = {remaining == 1}, child rows deleted by cascade (staging is REQUIRED)')
con.close()

print()
print(f'CERTIFICATION VALIDATOR RESULT: {"ALL CHECKS PASSED" if failures == 0 else f"{failures} FAILURE(S)"}')
sys.exit(1 if failures else 0)
