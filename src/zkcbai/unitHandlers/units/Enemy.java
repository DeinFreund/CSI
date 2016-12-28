/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers.units;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Unit;
import com.springrts.ai.oo.clb.UnitDef;
import com.springrts.ai.oo.clb.WeaponDef;
import com.springrts.ai.oo.clb.WeaponMount;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import zkcbai.Command;
import zkcbai.UpdateListener;
import zkcbai.helpers.AreaChecker;
import zkcbai.helpers.Pathfinder;
import zkcbai.helpers.Pathfinder.MovementType;
import zkcbai.helpers.ZoneManager;
import zkcbai.helpers.ZoneManager.Area;
import zkcbai.helpers.ZoneManager.Zone;

/**
 *
 * @author User
 */
public class Enemy {

    protected static final float regen = 0.0005f;
    protected static int idCounter = 0;
    protected Unit unit;
    protected final int id;
    protected int unitId;
    protected final OOAICallback clbk;
    protected final Command command;
    protected UnitDef unitDef;
    protected boolean neverSeen = true;
    protected float maxVelocity = 0f;
    protected AIFloat3 lastPos = new AIFloat3();
    protected AIFloat3 lastVel = new AIFloat3();
    protected AIFloat3 lastPosPossible;
    protected boolean alive = true;
    protected float maxRange = 0;
    protected boolean isBuilding;
    protected int lastSeen = 0;
    protected float health = 0;
    protected AIFloat3 lastAccPos;
    protected int lastAccPosTime = -1;
    protected boolean inLOS = false;
    protected float metalcost = 0;
    protected float dps = -1;
    protected boolean antiair = false;
    protected boolean isFlier = false;
    private static Set<UnitDef> aaUnits;

    private final float warriorspeed;

    public Enemy(Unit u, Command cmd, OOAICallback clbk) {
        this(u.getPos(), cmd, clbk);
        setUnitId(u.getUnitId());
        setUnit(u);
    }

    protected Enemy(AIFloat3 pos, Command cmd, OOAICallback clbk) {
        id = idCounter--;
        unitId = id;
        this.clbk = clbk;
        command = cmd;
        lastPos = lastPosPossible = pos;
        isBuilding = false;
        lastSeen = cmd.getCurrentFrame();

        if (aaUnits == null) {
            aaUnits = new HashSet<>();

            aaUnits.add(clbk.getUnitDefByName("corrl"));
            aaUnits.add(clbk.getUnitDefByName("corllt"));
            aaUnits.add(clbk.getUnitDefByName("gunshipsupport"));//rapier
            aaUnits.add(clbk.getUnitDefByName("fighter"));
            aaUnits.add(clbk.getUnitDefByName("slowmort")); //moderator
            aaUnits.add(clbk.getUnitDefByName("cormist")); //moderator
        }
        warriorspeed = clbk.getUnitDefByName("armwar").getSpeed();
    }

    private int deadpollFrame = -1;
    private int destroyFrame = -1;

    private void polledDead() {
        if (alive || command.getCurrentFrame() == destroyFrame) {
            return;
        }
        command.debug("polled dead enemy " + hashCode());
        command.debugStackTrace();;
        if (deadpollFrame == command.getCurrentFrame() && deadpollFrame != destroyFrame && command.getCurrentFrame() - deadpollFrame > 0) {
            destroyed();
            command.debug("Dead polled too often: destroying enemy " + hashCode());
            destroyMyself();
            try {
                throw new RuntimeException("Polled dead enemy " + hashCode());
            } catch (Exception ex) {
                command.debug("", ex);
            }
        }
        deadpollFrame = command.getCurrentFrame();
    }

    public MovementType getMovementType() {
        if (getDef().isAbleToFly()) {
            return Pathfinder.MovementType.air;
        }
        return Pathfinder.MovementType.getMovementType(getDef());
    }

    private int lastDestroyQueue = -1;
    
    public void destroyMyself() {
        if (lastDestroyQueue == command.getCurrentFrame()) return;
        lastDestroyQueue = command.getCurrentFrame();
        final Enemy meMyselfAndI = this;
        command.debug("queued destroy of " + (unitDef != null ? unitDef.getHumanName() : hashCode()));
        command.addSingleUpdateListener(new UpdateListener() {
            @Override
            public void update(int frame) {

                if (!alive) {
                    return;
                }
                command.debug("executed destroy");
                command.enemyDestroyed(meMyselfAndI, null);
            }
        }, command.getCurrentFrame() + 1);
    }

    public Area getArea() {
        return command.areaManager.getArea(getPos());
    }

    /**
     * only use when you can't use a UnitDestroyedListener instead
     *
     * @return
     */
    public boolean isAlive() {
        /*
        if (alive && isVisible(true) && unit.getHealth() < 0.00001f && !isBuilding) {
            command.mark(unit.getPos(), "missed " + (unitDef != null ? unitDef.getHumanName() : "-") + " death event " + hashCode());
            destroyMyself();
        }*/
        return alive;
    }

    public AIFloat3 getLastAccuratePos() {
        if (unit != null && unit.getHealth() > 0) {
            lastAccPos = unit.getPos();
            lastAccPosTime = command.getCurrentFrame();
        }
        return lastAccPos;
    }

    public int getLastAccuratePosTime() {
        return lastAccPosTime;
    }
    
    public boolean hasBeenSeen(){
        return !neverSeen;
    }

    protected int lastPosCheck = -100;

    public AIFloat3 getPos() {
        if (!isAlive()) {
            polledDead();
        }
        long time = System.nanoTime();
        if (command.getCurrentFrame() - lastSeen < 600) {
            AIFloat3 likelyPos = new AIFloat3(lastVel);
            likelyPos.scale(command.getCurrentFrame() - lastPosCheck);
            likelyPos.add(lastPosPossible);
            if (command.areaManager.getArea(likelyPos).isReachable()) {
                lastPosPossible = likelyPos;
            }
        }
        lastPosCheck = command.getCurrentFrame();

        if ((shouldBeVisible(lastPosPossible) && !isVisible())) {
            //command.debug(getDef().getHumanName() + " correcting visible position");
            update(command.getCurrentFrame());
        }
        time = System.nanoTime() - time;
        if (time > 0.5e6) {
            command.debug("getpos took " + time + "ns");
        }
        if (isBuilding() && !neverSeen) {
            return lastPos;
        }
        if (unit != null) {
            AIFloat3 pos = unit.getPos();
            if (pos.length() > 0) {
                lastPos = lastPosPossible = pos;
                lastSeen = command.getCurrentFrame();
                lastVel = getVel();
                return new AIFloat3(lastPos);
            }
        }
        return new AIFloat3(lastPosPossible);
    }

    public AIFloat3 getVel() {
        if (isVisible() && getUnit() != null) {
            return new AIFloat3(getUnit().getVel());
        }
        return new AIFloat3();
    }

    public UnitDef getDef() {
        long time = System.nanoTime();
        if (!isAlive()) {
            polledDead();
        }/*
        if (unit != null && unit.getDef() != null) {
            return unit.getDef();
        }*/
        if (unitDef == null) {
            identify();
        }

        time = System.nanoTime() - time;
        if (time > 0.5e6) {
            command.debug("getdef took " + time + "ns");
        }
        return unitDef;
    }

    protected void setUnitId(int id) {
        this.unitId = id;
        //command.debug("Set unit id to " + id);
    }

    public float getHealth() {
        if (!isAlive()) {
            polledDead();
        }
        if (isVisible(true)) {
            health = unit.getHealth();
        }
        return health;
    }

    public float getRelativeHealth() {
        if (!isAlive()) {
            polledDead();
        }
        return health / getDef().getHealth();
    }

    /**
     * wrong damage for carriers/wolverine/puppy/tacnuke/felon/scorcher/fire
     *
     * @return real damage DPS for units with real damage and magic damage DPS for units with exclusively magic effects
     */
    public float getDPS() {
        if (!isAlive()) {
            polledDead();
        }
        if (dps < 0) {
            identify();
        }
        if (dps > 0 && getDef().getWeaponMounts().isEmpty()) {
            command.debug("WubWub damage without weapon(" + getDef().getHumanName() + ")");
            dps = 0;
        }
        return dps;
    }

    protected void setUnit(Unit unit) {
        this.unit = unit;
        unitDef = unit.getDef();
        identify();
    }

    public Unit getUnit() {
        if (!isAlive()) {
            polledDead();
        }
        return unit;
    }

    public boolean isCommander() {
        return getDef().getCustomParams().containsKey("commtype");
    }

    public int getUnitId() {
        if (!isAlive()) {
            polledDead();
        }
        return unitId;
    }

    public float getMaxRange() {
        return maxRange;
    }

    /**
     *
     * @return cached last resource
     */
    public AIFloat3 getLastPos() {
        return lastPos;
    }

    /**
     *
     * @return cached last resource
     */
    public AIFloat3 getLastPosPossible() {
        return lastPosPossible;
    }

    public float distanceTo(AIFloat3 trg) {
        if (!isAlive()) {
            polledDead();
        }
        AIFloat3 pos = getPos();
        return (float)Math.sqrt((trg.x - pos.x)*(trg.x - pos.x) + (trg.z - pos.z) * (trg.z - pos.z));
    }

    public boolean shouldBeVisible(AIFloat3 pos) {
        if (!isAlive()) {
            polledDead();
        }
        if (command.getCurrentFrame() - lastSeen <= 10) {
            return false;
        }
        if (getDef().getCloakCost() <= 0 || getDef().isAbleToRepair() || timeSinceLastSeen() > 700 || getDef().isAbleToFly()) {
            return command.radarManager.isInRadar(pos) || command.losManager.isInLos(pos);
        } else {
            return !clbk.getFriendlyUnitsIn(pos, Math.max(35f, 0.9f * getDef().getDecloakDistance())).isEmpty();
        }

    }

    public boolean isVisible() {
        return isVisible(false);
    }

    public boolean isVisible(boolean inLos) {
        if (unit == null) {
            return false;
        }
        return (!inLos || this.inLOS) && unit.getPos().length() > 0;
    }

    private final AreaChecker invisible = new AreaChecker() {

        @Override
        public boolean checkArea(ZoneManager.Area a) {
            if (getDef().getCloakCost() <= 0 || getDef().isAbleToRepair() || timeSinceLastSeen() > 700 || getDef().isAbleToFly()) {
                return !(a.isVisible() || command.getCurrentFrame() - a.getLastVisible() < 30 * 60 * 3) && a.getZone() == Zone.hostile;
            } else {
                return clbk.getFriendlyUnitsIn(a.getPos(), a.getEnclosingRadius() + 40 + getDef().getDecloakDistance()).isEmpty();
            }
        }

    };

    public Collection<WeaponDef> getWeaponDefs() {
        return command.getWeaponDefs(getDef());
    }

    /**
     *
     * @return time since last in radar/los in frames
     */
    public int timeSinceLastSeen() {
        if (!isAlive()) {
            polledDead();
        }
        long time = System.nanoTime();
        if (isBuilding) {
            return 0;
        }
        if (command.getCurrentFrame() - lastPosCheck > 5) {
            getPos();
        }
        time = System.nanoTime() - time;
        if (time > 0.5e6) {
            command.debug("timesincelastseen took " + time + "ns");
        }
        if (Command.distance2D(lastPos, lastPosPossible) > 1000) {
            return (command.getCurrentFrame() - lastSeen) * 3 + 180;
        }
        return command.getCurrentFrame() - lastSeen;
    }

    public boolean isTimedOut() {
        if (!isAlive()) {
            polledDead();
        }
        long time = System.nanoTime();
        boolean retval = timeSinceLastSeen() > 1000 * 100 / Math.max(getDef().getSpeed(), warriorspeed) * 30 / Math.max(30, command.getCommandDelay());
        time = System.nanoTime() - time;
        if (time > 0.5e6) {
            command.debug("istimedout took " + time + "ns");
        }
        return retval;
    }

    private int lastUpdate = 0;

    public void update(int frame) {
        long time0 = System.nanoTime();
        lastUpdate = frame;
        if (!isAlive()) {
            polledDead();
            return;
        }
        if (timeSinceLastSeen() > 30 * 60 * (int) getDef().getCost(command.metal) / 50) {
            destroyMyself();
        }
        long time1 = System.nanoTime();
        health = Math.min(getDef().getHealth(), health + (frame - lastUpdate) * regen * getDef().getHealth());
        if (unit != null && unit.getHealth() > 0) {
            //in LOS
            health = unit.getHealth();
            lastAccPos = unit.getPos();
            lastAccPosTime = frame;
            if (unit.getPos().y < -10) {
                destroyMyself();
                command.debug("Destroying underwater " + getDef().getHumanName() + " " + hashCode());
            }

        }
        //command.debug(getDef().getHumanName() + " : " + shouldBeVisible(lastPosPossible) + " " + isVisible());

        long time2 = System.nanoTime();
        if (unit != null && unit.getPos().length() > 0) {
            //in Radar
            lastPos = lastPosPossible = unit.getPos();
            lastSeen = command.getCurrentFrame();
            lastVel = unit.getVel();
        } else if (shouldBeVisible(lastPosPossible)) {
            //command.debug("new pos for " + getDef().getHumanName());
            if (isBuilding) {
                command.debug("Building not actually there: " + getDef().getHumanName());
                //lastPos = lastPosPossible = new AIFloat3();
                destroyMyself();
            } else {
                Area npos;
                AIFloat3 likelyPos = new AIFloat3(lastVel);
                likelyPos.scale(Math.min(600, command.getCurrentFrame() - lastSeen));
                likelyPos.add(lastPos);
                npos = command.areaManager.getArea(likelyPos).getNearestArea(invisible, getMovementType());
                if (npos != null) {
                    lastPosPossible = npos.getPos();
                } else {
                    //command.debug("No possible pos found for " + getDef().getHumanName());
                    destroyMyself();
                }
            }
        } else {
            /*if (getDef().getHumanName().equalsIgnoreCase("blastwing")){
                command.mark(lastPosPossible, "invisible blastwing");
            }*/
        }

        long time3 = System.nanoTime();
        if (neverSeen && unit != null && unit.getVel().length() > maxVelocity) {
            identify();
        }

        long time4 = System.nanoTime();

        if (time4 - time0 > 1e6) {
            command.debug("Update of enemy  " + getDef().getHumanName() + " took " + (time4 - time3) + "/" + (time3 - time2) + "/" + (time2 - time1) + "/" + (time1 - time0) + "ns. ");
        }

        //command.mark(getPos(), getPos().toString());
    }

    public void enterLOS() {
        if (!isAlive()) {
            polledDead();
        }

        if (neverSeen) {
            health = unit.getHealth();
            unitDef = unit.getDef();
            identify();
            if (unitDef.getName().equals("cormex")) {
                command.areaManager.getNearestMex(getPos()).setEnemyMex(this);
            }
        }
        inLOS = true;
        neverSeen = false;
        lastSeen = command.getCurrentFrame();
        update(command.getCurrentFrame());
    }

    public boolean isAntiAir() {
        return antiair;
    }

    public boolean isAbleToFly() {
        return isFlier;
    }

    public float getMetalCost() {
        return metalcost;
    }

    public boolean isBuilding() {
        if (!isAlive()) {
            polledDead();
        }
        return isBuilding;
    }

    public boolean isIdentifiedByRadar() {
        if (!isAlive()) {
            polledDead();
        }
        return neverSeen && getDef().getSpeed() > 0.5;
    }

    public void identify() {
        if (!isAlive()) {
            polledDead();
        }
        long time = System.nanoTime();

        if (unit != null && unit.getDef() != null) {
            unitDef = unit.getDef();
        }
        if (unit != null && neverSeen && (unitDef == null || unitDef.getSpeed() < maxVelocity - 0.001f)) {
            maxVelocity = Math.max(unit.getVel().length(), maxVelocity);
            if (command.getEnemyUnitDefSpeedMap().ceilingEntry(unit.getVel().length() - 1e-6f) != null) {
                unitDef = command.getEnemyUnitDefSpeedMap().ceilingEntry(unit.getVel().length() - 1e-6f).getValue();
            }
        }
        if (unitDef == null) {
            unitDef = clbk.getUnitDefByName("armpw");
        }
        if (unitDef.getName().equalsIgnoreCase("wolverine_mine")) {
            unitDef = clbk.getUnitDefByName("armwin");
            destroyMyself();
        }
        maxRange = 0;
        for (WeaponMount wm : unitDef.getWeaponMounts()) {
            maxRange = Math.max(wm.getWeaponDef().getRange(), maxRange);
        }
        health = unitDef.getHealth();
        metalcost = unitDef.getCost(command.metal);
        command.debug("Identified " + unitDef.getHumanName() + "(" + hashCode() + ") with metal cost " + metalcost);
        dps = Command.getDPS(getDef());
        isBuilding = getDef().getSpeed() <= 0.01;
        antiair = dps > 0 && getDef().getWeaponMounts().get(0).getWeaponDef().isAbleToAttackGround() == false || aaUnits.contains(getDef());
        isFlier = getDef().isAbleToFly();
        time = System.nanoTime() - time;
        if (time > 0.1e6) {
            command.debug("Identification of " + getDef().getHumanName() + " took " + time + " ns.");
        }
    }

    public void enterRadar() {
        lastSeen = command.getCurrentFrame();
        update(command.getCurrentFrame());
    }

    public void leaveLOS() {
        command.debug(getDef().getHumanName() + " left LOS");
        inLOS = false;
        lastSeen = command.getCurrentFrame();
        update(command.getCurrentFrame());
    }

    public void leaveRadar() {
        lastSeen = command.getCurrentFrame();
        update(command.getCurrentFrame());
    }

    public void destroyed() {
        if (!alive) {
            return;
        }
        //command.mark(getPos(), "dead");
        command.debug("Enemy " + hashCode() + "(" + getDef().getHumanName() + ") has been destroyed");
        alive = false;
        destroyFrame = command.getCurrentFrame();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Enemy) {
            return equals((Enemy) o);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return id;
    }

    public boolean equals(Enemy e) {
        if (e == null) {
            return false;
        }
        return hashCode() == e.hashCode();
    }
}
