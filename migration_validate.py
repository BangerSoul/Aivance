import sqlite3, re, json, os, sys

KOTLIN = 'core/database/src/main/java/com/bangersoul/aivance/core/database/AivanceDatabase.kt'
BASE = 'core/database/schemas/com.bangersoul.aivance.core.database.AivanceDatabase'

src = open(KOTLIN, encoding='utf-8', errors='replace').read()

# ---- extract migrations ----
blocks = {}
pat = re.compile(r'val MIGRATION_(\d+)_(\d+) = object : Migration\(\d+, \d+\)')
matches = list(pat.finditer(src))
for i, m in enumerate(matches):
    start = m.end()
    end = matches[i + 1].start() if i + 1 < len(matches) else len(src)
    body = src[start:end]
    stmts = []
    for em in re.finditer(r'db\.execSQL\(\s*("(?:\\.|[^"\\])*")', body, re.S):
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
                if nxt == 'u' and j + 6 <= len(raw):
                    try:
                        out.append(chr(int(raw[j + 2:j + 6], 16))); j += 6; continue
                    except ValueError:
                        pass
                out.append(nxt); j += 2; continue
            out.append(c); j += 1
        stmts.append(''.join(out))
    blocks[(int(m.group(1)), int(m.group(2)))] = stmts

# ---- schema loaders ----
def load_json(v):
    with open(f'{BASE}/{v}.json') as f:
        return json.load(f)['database']

def expected(v):
    db = load_json(v)
    tables = {}
    for e in db['entities']:
        pk_names = (e.get('primaryKey') or {}).get('columnNames', [])
        pkpos = {name: i + 1 for i, name in enumerate(pk_names)}
        cols = []
        for f in e['fields']:
            cols.append({
                'name': f['columnName'],
                'type': f.get('affinity', 'TEXT'),
                'notNull': f.get('notNull', False),
                'default': f.get('defaultValue'),
                'pk': pkpos.get(f['columnName'], 0),
            })
        inds = []
        for idx in (e.get('indices') or []):
            inds.append({'name': idx['name'], 'unique': idx.get('unique', False), 'cols': idx['columnNames']})
        fks = []
        for fk in (e.get('foreignKeys') or []):
            fks.append({'table': fk['table'], 'from': fk['columns'], 'to': fk['referencedColumns'],
                        'onDelete': fk.get('onDelete', 'NO ACTION'), 'onUpdate': fk.get('onUpdate', 'NO ACTION')})
        tables[e['tableName']] = {'cols': cols, 'inds': inds, 'fks': fks}
    return tables

def build_base(v, con):
    db = load_json(v)
    for e in db['entities']:
        sql = e['createSql'].replace('${TABLE_NAME}', e['tableName'])
        con.execute(sql)
        for idx in (e.get('indices') or []):
            cols = ', '.join('`%s`' % c for c in idx['columnNames'])
            uniq = ' UNIQUE' if idx.get('unique') else ''
            con.execute(f"CREATE{uniq} INDEX IF NOT EXISTS `{idx['name']}` ON `{e['tableName']}` ({cols})")

def seed_version(v, con):
    """Insert exactly one row into every table at version v, in FK-dependency order,
    generating values from the schema JSON itself. Returns dict table -> True if seeded."""
    db = load_json(v)
    ents = {e['tableName']: e for e in db['entities']}
    # topological order: parents before children
    ordered = []
    remaining = set(ents.keys())
    while remaining:
        progress = False
        for t in list(remaining):
            fks = ents[t].get('foreignKeys') or []
            deps = {fk['table'] for fk in fks}
            if deps <= set(ordered):
                ordered.append(t)
                remaining.discard(t)
                progress = True
        if not progress:
            ordered.extend(sorted(remaining))
            break
    seeded = {}
    for t in ordered:
        e = ents[t]
        cols, vals = [], []
        ok = True
        for f in e['fields']:
            name = f['columnName']
            aff = f.get('affinity', 'TEXT')
            nn = f.get('notNull', False)
            pkpos = (e.get('primaryKey') or {}).get('columnNames', []).index(name) + 1 if name in (e.get('primaryKey') or {}).get('columnNames', []) else 0
            if pkpos == 1:
                # primary key: choose deterministic id
                if aff == 'INTEGER':
                    vv = 1
                else:
                    vv = 'pk1'
            elif nn:
                vv = 1 if aff in ('INTEGER', 'REAL') else 'x'
            else:
                vv = None
            cols.append(name)
            vals.append(vv)
        try:
            ph = ', '.join('?' for _ in vals)
            cl = ', '.join('`%s`' % c for c in cols)
            con.execute(f'INSERT INTO `{t}` ({cl}) VALUES ({ph})', vals)
            seeded[t] = True
        except Exception:
            pass
    return seeded

def table_cols(con, t):
    return [{'name': r[1], 'type': r[2], 'notNull': bool(r[3]), 'default': r[4], 'pk': r[5]}
            for r in con.execute(f'PRAGMA table_info("{t}")').fetchall()]

def table_inds(con, t):
    out = []
    for row in con.execute(f'PRAGMA index_list("{t}")').fetchall():
        name, uniq = row[1], row[2]
        if name.startswith('sqlite_'):
            continue
        cols = [r[2] for r in con.execute(f'PRAGMA index_info("{name}")').fetchall()]
        out.append({'name': name, 'unique': bool(uniq), 'cols': cols})
    return out

def table_fks(con, t):
    return [{'table': r[2], 'from': [r[3]], 'to': [r[4]], 'onUpdate': r[5], 'onDelete': r[6]}
            for r in con.execute(f'PRAGMA foreign_key_list("{t}")').fetchall()]

def compare(v, exp, con):
    errors = []
    act_tables = {r[0] for r in con.execute("SELECT name FROM sqlite_master WHERE type='table'") if not r[0].startswith('sqlite_')}
    exp_tables = set(exp.keys())
    if act_tables != exp_tables:
        errors.append(f"table set mismatch: extra={sorted(act_tables - exp_tables)} missing={sorted(exp_tables - act_tables)}")
    for t in sorted(exp_tables):
        if t not in act_tables:
            continue
        exp_cols = {c['name']: c for c in exp[t]['cols']}
        act_cols = {c['name']: c for c in table_cols(con, t)}
        for cn, ec in exp_cols.items():
            ac = act_cols.get(cn)
            if ac is None:
                errors.append(f"{t}.{cn}: MISSING in actual")
                continue
            if ac['notNull'] != ec['notNull']:
                errors.append(f"{t}.{cn}: notNull actual={ac['notNull']} expected={ec['notNull']}")
            if (ac['default'] or None) != (ec['default'] or None):
                errors.append(f"{t}.{cn}: default actual={ac['default']!r} expected={ec['default']!r}")
            if ac['pk'] != ec['pk']:
                errors.append(f"{t}.{cn}: pk pos actual={ac['pk']} expected={ec['pk']}")
        for cn in act_cols:
            if cn not in exp_cols:
                errors.append(f"{t}.{cn}: EXTRA column in actual")
        exp_inds = {(i['name'], tuple(i['cols']), i['unique']) for i in exp[t]['inds']}
        act_inds = {(i['name'], tuple(i['cols']), i['unique']) for i in table_inds(con, t)}
        if exp_inds != act_inds:
            errors.append(f"{t}: indices mismatch\n    expected={sorted(exp_inds)}\n    actual  ={sorted(act_inds)}")
        exp_fks = {(f['table'], tuple(f['from']), tuple(f['to']), f['onDelete'], f['onUpdate']) for f in exp[t]['fks']}
        act_fks = {(f['table'], tuple(f['from']), tuple(f['to']), f['onDelete'], f['onUpdate']) for f in table_fks(con, t)}
        if exp_fks != act_fks:
            errors.append(f"{t}: FK mismatch\n    expected={sorted(exp_fks)}\n    actual  ={sorted(act_fks)}")
    return errors

print("Single-step validation (FK ON, schema equality + strict data preservation):")
total_issues = 0
for f in range(5, 25):
    t = f + 1
    stmts = blocks.get((f, t))
    if stmts is None:
        print(f"  {f}->{t}: NO MIGRATION FOUND")
        total_issues += 1
        continue
    if not os.path.exists(f'{BASE}/{f}.json') or not os.path.exists(f'{BASE}/{t}.json'):
        print(f"  {f}->{t}: schema JSON missing, skipped")
        continue
    con = sqlite3.connect(':memory:')
    con.execute("PRAGMA foreign_keys=ON")
    build_base(f, con)
    seeded = seed_version(f, con)
    errors = []
    try:
        for s in stmts:
            con.execute(s)
    except Exception as e:
        errors.append(f"SQL THREW: {e}")
    if not errors:
        errors = compare(t, expected(t), con)
    # strict preservation: every seeded table that SURVIVES into the target schema
    # must still exist with >= seeded rows (intentionally dropped tables are exempt)
    if not errors:
        exp_target = expected(t)
        for tab in seeded:
            if tab not in exp_target:
                continue  # table intentionally dropped by this migration
            if tab not in {r[0] for r in con.execute("SELECT name FROM sqlite_master WHERE type='table'")}:
                errors.append(f"DATA LOSS: seeded table {tab} no longer exists")
            else:
                after = con.execute(f'SELECT COUNT(*) FROM "{tab}"').fetchone()[0]
                if after < 1:
                    errors.append(f"DATA LOSS: seeded table {tab} now has {after} rows")
    if errors:
        print(f"  {f}->{t}: FAIL ({len(errors)} issues)")
        for e in errors[:10]:
            print(f"      - {e}")
        if len(errors) > 10:
            print(f"      ... and {len(errors)-10} more")
        total_issues += len(errors)
    else:
        n = len(seeded)
        print(f"  {f}->{t}: OK ({n} tables seeded + preserved)")
    con.close()

print(f"\nTOTAL ISSUES: {total_issues}")
sys.exit(1 if total_issues else 0)
