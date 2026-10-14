/*
 *  Armoury
 *  Taijitu -- the recoverable cache
 *
 *  Copyright(c) 1998, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/22/2026 mir0n  created: night-watch reaction policy -- what the director does when the two
 *                   monads' checksums DISAGREE. LOG (record only), SWAP (promote the freshly-loaded
 *                   shadow), TERMINATE (exit the process for an orchestrator restart).
 * 10/14/2026 mir0n  v2.0.0 -- moved to armoury-taijitu: package pro.mir0n.utils.taijitu -> pro.mir0n.taijitu
 */
package pro.mir0n.taijitu;

/**
 * How the Taijitu director reacts to a night-watch checksum MISMATCH (the two monads disagree):
 *   - LOG       -- record the drift, keep serving (the conservative default).
 *   - SWAP      -- promote the freshly-loaded shadow to serving (swapYinYang).
 *   - TERMINATE -- exit the process so an orchestrator restarts it from a clean load.
 */
public enum MismatchAction {
    LOG,
    SWAP,
    TERMINATE
}
