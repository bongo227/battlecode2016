package turret_scout;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Signal;
import battlecode.common.Team;

public class BotTurret extends Globals {

	public static int motherId = 0;
	public static MapLocation motherLocation = null;
	public static boolean isHappyShooting = false;
	
	private static Signal[] currentSignals;

	public static void loop() {
		update();
		Debug.init("spotting");
		while (true) {
			try {
				turn();
			} catch (Exception e) {
				e.printStackTrace();
			}
			Clock.yield();
		}
	}

	public static void updateMotherId(int range) {
		RobotInfo[] infos = rc.senseNearbyRobots(range, us);
		for (RobotInfo info : infos) {
			if (info.type == RobotType.ARCHON) {
				motherId = info.ID;
				motherLocation = info.location;
				break;
			}
		}
	}

	public static void updateMotherLocation() throws GameActionException {
		if (rc.canSenseRobot(motherId)) {
			motherLocation = rc.senseRobot(motherId).location;
		} else {
			updateMotherId(mySensorRadiusSquared);
		}
	}

	public static boolean shootEnemy() throws GameActionException {
		MapLocation bestLoc = null;
		double bestLocHealth = Double.MAX_VALUE;
		RobotInfo[] enemies = rc.senseNearbyRobots(mySensorRadiusSquared, them);
		for (RobotInfo enemy : enemies) {
			if (!rc.canAttackLocation(enemy.location)) {
				continue;
			}
			if (enemy.health < bestLocHealth) {
				bestLoc = enemy.location;
				bestLocHealth = enemy.health;
			}
		}
		if (bestLoc != null) {
			rc.attackLocation(bestLoc);
			return true;
		}
		RobotInfo[] zombies = rc.senseNearbyRobots(mySensorRadiusSquared, Team.ZOMBIE);
		for (RobotInfo zombie : zombies) {
			if (!rc.canAttackLocation(zombie.location)) {
				continue;
			}
			if (zombie.health < bestLocHealth) {
				bestLoc = zombie.location;
				bestLocHealth = zombie.health;
			}
		}
		if (bestLoc != null) {
			rc.attackLocation(bestLoc);
			return true;
		}
		Debug.indicate("spotting", 0, "going to check for spotting messages; message queue length = " + currentSignals.length);
		for (Signal sig : currentSignals) {
			MapLocation target = Messages.tryParseTurretSignal(sig);
			Debug.indicate("spotting", 1, "got target = " + target);
			if (target != null && rc.canAttackLocation(target)) {
				Debug.indicate("spotting", 2, "attacking spotting target!");				
				rc.attackLocation(target);
				return true;
			}
		}
		
		return false;
	}

	private static void turn() throws GameActionException {
		Globals.update();
		currentSignals = rc.emptySignalQueue();
		if (rc.isWeaponReady()) {
			if (shootEnemy()) {
				isHappyShooting = true;
			} else {
				isHappyShooting = false;
			}
		}
	}
}
