/*
 *  Armoury
 *  Hauberk -- the generic Gatling test harness
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: class-level @SimulationInfo annotation carrying the one-line catalog description -- read reflectively by ListCommand
 */
package pro.mir0n.hauberk.simulations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * One-line catalog description for a {@code HauberkSimulation}. Read by
 * {@code hauberk list} via reflection on the class object -- no
 * instantiation needed, which matters because Gatling's
 * {@code Simulation} constructor refuses direct {@code new} calls outside
 * the Gatling runner. The annotation is the only way to attach a piece
 * of catalog metadata that {@code list} can read without firing the sim.
 *
 * Every concrete Simulation should carry this; the v1.2.4 hauberk has it
 * on all 16. A small unit test (HauberkSimulationCatalogTest) asserts
 * presence so the contract is enforced at build time.
 *
 * <pre>
 * &#64;SimulationInfo("End-to-end entity walk: office/user/account create + ...")
 * public class EntitySmokeSimulation extends HauberkSimulation { ... }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SimulationInfo {
    /** One-line human-readable description; should fit ~80 columns. */
    String value();
}
