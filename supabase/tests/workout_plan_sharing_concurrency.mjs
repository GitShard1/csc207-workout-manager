// Run with Node.js and psql against a disposable, migrated PostgreSQL or Supabase database.
// Set TEST_DATABASE_URL to its direct connection URL for a database role that can seed auth.users.
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { setTimeout as delay } from 'node:timers/promises';

const databaseUrl = process.env.TEST_DATABASE_URL;
if (!databaseUrl) {
    throw new Error('Set TEST_DATABASE_URL to a disposable migrated database.');
}
const connection = new URL(databaseUrl);
if (!['postgres:', 'postgresql:'].includes(connection.protocol)) {
    throw new Error('TEST_DATABASE_URL must be a PostgreSQL connection URL.');
}
const pgEnvironment = {
    ...process.env,
    PGHOST: connection.hostname.replace(/^\[|\]$/g, ''),
    PGPORT: connection.port || '5432',
    PGDATABASE: decodeURIComponent(connection.pathname.slice(1)),
    PGUSER: decodeURIComponent(connection.username),
    PGPASSWORD: decodeURIComponent(connection.password),
    PGSSLMODE: connection.searchParams.get('sslmode') || process.env.PGSSLMODE || 'prefer',
    PGCONNECT_TIMEOUT: '5',
};
delete pgEnvironment.TEST_DATABASE_URL;

const owner = randomUUID();
const recipient = randomUUID();
const source = randomUUID();
const run = owner.slice(0, 8);
const applications = new Set();

function literal(value) {
    return "'" + String(value).replaceAll("'", "''") + "'";
}

function session(applicationName) {
    const child = spawn(process.env.PSQL ?? 'psql', ['-XwqAt', '-v', 'ON_ERROR_STOP=1', '-f', '-'],
        { env: { ...pgEnvironment, PGAPPNAME: applicationName } });
    applications.add(child);
    let stdout = '';
    let stderr = '';
    child.stdout.setEncoding('utf8');
    child.stderr.setEncoding('utf8');
    child.stdout.on('data', chunk => { stdout += chunk; });
    child.stderr.on('data', chunk => { stderr += chunk; });
    child.stdin.on('error', () => { /* The child process exit reports command errors. */ });
    const completed = new Promise((resolve, reject) => {
        child.once('error', reject);
        child.once('close', code => {
            applications.delete(child);
            if (code === 0) {
                resolve(stdout.trim());
            } else {
                reject(new Error('psql failed in ' + applicationName + ': ' + stderr.replaceAll(databaseUrl, '[database URL]').trim()));
            }
        });
    });
    // A held session may fail while the coordinator is still polling PostgreSQL.
    completed.catch(() => {});
    return { child, completed };
}

function query(sql) {
    const connection = session('share_test_' + run + '_probe');
    connection.child.stdin.end(sql + '\n');
    return connection.completed;
}

function asUser(userId, sql) {
    return 'begin;\n'
        + 'set local statement_timeout = 20000;\n'
        + 'set local idle_in_transaction_session_timeout = 20000;\n'
        + 'set local role authenticated;\n'
        + 'set local request.jwt.claim.sub = ' + literal(userId) + ';\n'
        + sql + '\n';
}

async function waitFor(condition, message) {
    const deadline = Date.now() + 10000;
    do {
        if (await query('select exists (' + condition + ');') === 't') {
            return;
        }
        await delay(75);
    } while (Date.now() < deadline);
    assert.fail(message);
}

async function overlap(label, firstSql, secondSql) {
    const firstName = 'share_test_' + run + '_' + label + '_first';
    const secondName = 'share_test_' + run + '_' + label + '_second';
    const first = session(firstName);
    first.child.stdin.write(firstSql);
    try {
        await waitFor('select 1 from pg_stat_activity where application_name = ' + literal(firstName)
            + " and state = 'idle in transaction' and query like 'select public.%'",
            label + ': first operation did not reach its commit barrier');

        const second = session(secondName);
        second.child.stdin.end(secondSql + 'commit;\n');
        try {
            await waitFor('select 1 from pg_stat_activity as first_actor cross join pg_stat_activity as second_actor'
                + ' where first_actor.application_name = ' + literal(firstName)
                + ' and second_actor.application_name = ' + literal(secondName)
                + ' and first_actor.pid = any(pg_blocking_pids(second_actor.pid))',
                label + ': second operation did not wait on the first operation');
            first.child.stdin.end('commit;\n');
            const [firstResult, secondResult] = await Promise.all([first.completed, second.completed]);
            return [JSON.parse(firstResult), JSON.parse(secondResult)];
        } finally {
            second.child.kill();
        }
    } finally {
        first.child.kill();
        await first.completed.catch(() => {});
    }
}

async function listRecipientNames() {
    const result = await query('select coalesce(json_agg(name order by name), \'[]\'::json) '
        + 'from public.workout_plans where owner_id = ' + literal(recipient) + ';');
    return JSON.parse(result);
}

function copySql(code, revision, request) {
    return asUser(recipient, 'select public.copy_shared_workout_plan('
        + [code, revision, request].map(literal).join(',') + ');');
}

try {
    await query('begin; insert into auth.users (id, email, raw_user_meta_data) values '
        + '(' + literal(owner) + ',' + literal('share_owner_' + run + '@example.test') + ','
        + literal(JSON.stringify({ username: 'share_owner_' + run, display_name: 'Share Test Owner' })) + '),'
        + '(' + literal(recipient) + ',' + literal('share_rcpt_' + run + '@example.test') + ','
        + literal(JSON.stringify({ username: 'share_rcpt_' + run, display_name: 'Share Test Recipient' })) + ');'
        + 'insert into public.workout_plans (id, owner_id, name, exercises) values ('
        + [source, owner, 'Concurrency Plan',
            JSON.stringify([{ exercise_id: '1', name: 'Lunge', body_part: 'UPPER_LEGS', sets: 2, reps: 6 }])]
            .map(literal).join(',')
        + '); commit;');

    async function generateCode() {
        const result = JSON.parse(await query(
            asUser(owner, 'select public.generate_workout_plan_share(' + literal(source) + ');') + 'commit;'));
        assert.equal(result.status, 'generated');
        return result.code;
    }

    const code = await generateCode();
    const preview = JSON.parse(await query(
        asUser(recipient, 'select public.preview_shared_workout_plan(' + literal(code) + ');') + 'commit;'));
    assert.equal(preview.status, 'available');

    const request = randomUUID();
    const retrySql = copySql(code, preview.revision, request);
    const [first, retry] = await overlap('retry', retrySql, retrySql);
    assert.equal(first.status, 'saved');
    assert.deepEqual(retry, first);
    assert.deepEqual(await listRecipientNames(), ['Concurrency Plan']);
    console.log('PASS same-request concurrency returns the same single copy');

    const [duplicateA, duplicateB] = await overlap('names',
        copySql(code, preview.revision, randomUUID()),
        copySql(code, preview.revision, randomUUID()));
    assert.equal(duplicateA.status, 'saved');
    assert.equal(duplicateB.status, 'saved');
    assert.notEqual(duplicateA.id, duplicateB.id);
    assert.deepEqual((await listRecipientNames()).sort(),
        ['Concurrency Plan', 'Concurrency Plan (copy 2)', 'Concurrency Plan (copy)'].sort());
    console.log('PASS concurrent distinct requests resolve case-insensitive name collisions');

    const [revoked, blockedCopy] = await overlap('revoked',
        asUser(owner, 'select public.revoke_workout_plan_share(' + literal(source) + ');'),
        copySql(code, preview.revision, randomUUID()));
    assert.equal(revoked.status, 'revoked');
    assert.equal(blockedCopy.status, 'unavailable');
    assert.equal((await listRecipientNames()).length, 3);
    console.log('PASS concurrent revoke invalidates a waiting copy');
} finally {
    for (const child of applications) {
        child.kill();
    }
    await query('delete from auth.users where id in (' + [owner, recipient].map(literal).join(',') + ');');
}
