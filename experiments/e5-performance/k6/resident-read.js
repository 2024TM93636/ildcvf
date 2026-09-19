import http from 'k6/http';
import { check } from 'k6';

const TARGET_URL = __ENV.TARGET_URL;
const PROFILE = (__ENV.PROFILE || 'smoke').toLowerCase();
const RUN_ID = __ENV.RUN_ID || 'unspecified';
const PATH_TYPE = (__ENV.PATH_TYPE || 'UNSPECIFIED').toUpperCase();
const SUMMARY_FILE = __ENV.SUMMARY_FILE || '';

if (!TARGET_URL) {
    throw new Error('TARGET_URL is required.');
}

if (!['DIRECT', 'ILDCVF'].includes(PATH_TYPE)) {
    throw new Error('PATH_TYPE must be DIRECT or ILDCVF.');
}

const profiles = {
    smoke: {
        vus: 1,
        duration: '5s',
    },
    steady: {
        vus: 5,
        duration: '30s',
    },
    moderate: {
        vus: 10,
        duration: '30s',
    },
};

if (!profiles[PROFILE]) {
    throw new Error(
        `Unknown PROFILE '${PROFILE}'. Expected smoke, steady, or moderate.`
    );
}

export const options = {
    vus: profiles[PROFILE].vus,
    duration: profiles[PROFILE].duration,

    summaryTrendStats: [
        'avg',
        'med',
        'p(95)',
        'p(99)',
        'min',
        'max',
        'count',
    ],

    tags: {
        run_id: RUN_ID,
        path_type: PATH_TYPE,
        profile: PROFILE,
    },
};

export default function () {
    const response = http.get(TARGET_URL, {
        tags: {
            run_id: RUN_ID,
            path_type: PATH_TYPE,
            profile: PROFILE,
        },
    });

    check(response, {
        'status is 200': (r) => r.status === 200,
    });
}

export function handleSummary(data) {
    if (!SUMMARY_FILE) {
        return {
            stdout: JSON.stringify(data, null, 2),
        };
    }

    return {
        [SUMMARY_FILE]: JSON.stringify(data, null, 2),
    };
}