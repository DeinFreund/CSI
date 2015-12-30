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
import com.springrts.ai.oo.clb.WeaponMount;
import zkcbai.Command;
import zkcbai.UpdateListener;
import zkcbai.helpers.AreaChecker;
import zkcbai.helpers.Pathfinder;
import zkcbai.helpers.Pathfinder.MovementType;
import zkcbai.helpers.ZoneManager;

/**
 *
 * @author User
 */
public class Enemy implements UpdateListener {

    protected static final float regen = 0.01f;
    protected static int idCounter = 0;
    protected Unit unit;
    protected final int id;
    protected int unitId;
    protected final OOAICallback clbk;
    protected final Command command;
    protected UnitDef unitDef;
    protected boolean neverSeen = true;
    protected float maxVelocity = 0.5f;
    protected AIFloat3 lastPos;
    protected boolean alive = true;
    protected float maxRange = 0;
    protected boolean isBuilding;
    protected int lastSeen = 0;
    protected float health = 0;

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
        lastPos = pos;
        isBuilding = false;
        lastSeen = cmd.getCurrentFrame();
        getDef();

        cmd.addSingleUpdateListener(this, cmd.getCurrentFrame() + 40);
    }
    
    private int deadpollCounter = 0;
    
    private void polledDead(){
        deadpollCounter++;
        if (deadpollCounter > 4){
            command.enemyDestroyed(this, null);
            command.debug("Dead polled too often: destroying");
            try{
            throw new RuntimeException("Polled dead enemy " + hashCode());
            }catch(Exception ex){
                command.debug("",ex);
            }
        }
    }

    public MovementType getMovementType(){
        return Pathfinder.MovementType.getMovementType(getDef());
    }
    
    /**
     * only use when you can't use a UnitDestroyedListener instead
     * @return
     */
    public boolean isAlive() {
        return alive;
    }

    public AIFloat3 getPos() {
        if (!alive) {
            polledDead();
        }
        if (unit != null && unit.getPos().length() > 0) {
            return new AIFloat3(unit.getPos());
        }
        return new AIFloat3(lastPos);
    }

    public UnitDef getDef() {
        if (!alive) {
            polledDead();
        }
        if (unit != null && unit.getDef() != null) {
            return unit.getDef();
        }
        if (unitDef == null) {
            identify();
        }
        return unitDef;
    }

    protected void setUnitId(int id) {
        this.unitId = id;
        //command.debug("Set unit id to " + id);
    }

    public float getHealth() {
        if (!alive) {
            polledDead();
        }
        return health;
    }

    public float getRelativeHealth() {
        if (!alive) {
            polledDead();
        }
        return health / getDef().getHealth();
    }

    public float getDPS() {
        if (!alive) {
            polledDead();
        }
        float dps = 0;
        for (WeaponMount wm : getDef().getWeaponMounts()) {
            for (Float f : wm.getWeaponDef().getDamage().getTypes()) {
                dps += f / wm.getWeaponDef().getReload();
            }
        }
        return dps;
    }

    protected void setUnit(Unit unit) {
        this.unit = unit;
    }

    public Unit getUnit() {
        if (!alive) {
            polledDead();
        }
        return unit;
    }

    public int getUnitId() {
        if (!alive) {
            polledDead();
        }
        return unitId;
    }

    public float getMaxRange() {
        if (!alive) {
            polledDead();
        }
        return maxRange;
    }

    public float distanceTo(AIFloat3 trg) {
        if (!alive) {
            polledDead();
        }
        AIFloat3 pos = new AIFloat3(getPos());
        pos.sub(trg);
        pos.y = 0;
        return pos.length();
    }

    public boolean shouldBeVisible(AIFloat3 pos) {
        if (!alive) {
            polledDead();
        }
        if (getDef().getCloakCost() <= 0 || getDef().isAbleToRepair() || timeSinceLastSeen() > 700) {
            return command.radarManager.isInRadar(pos) || command.losManager.isInLos(pos);
        } else {
            return !clbk.getFriendlyUnitsIn(pos, getDef().getDecloakDistance()).isEmpty();
        }
    }

    public boolean isVisible() {
        return isVisible(false);
    }

    public boolean isVisible(boolean inLos) {
        if (unit == null) {
            return false;
        }
        return (!inLos && unit.getPos().length() > 0) || unit.getHealth() > 0;
    }

    private final AreaChecker invisible = new AreaChecker() {

        @Override
        public boolean checkArea(ZoneManager.Area a) {
            return !shouldBeVisible(a.getPos());
        }

    };

    public int timeSinceLastSeen() {
        if (!alive) {
            polledDead();
        }
        if (isBuilding) {
            return 0;
        }
        return command.getCurrentFrame() - lastSeen;
    }

    public boolean isTimedOut() {
        if (!alive) {
            polledDead();
        }
        return timeSinceLastSeen() > 2000 * 100 / Math.max(getDef().getSpeed(), 0.1);
    }

    @Override
    public void update(int frame) {
        if (!alive) {
            return;
        }
        health = Math.min(getDef().getHealth(), health + regen * getDef().getHealth());
        if (unit != null && unit.getHealth() > 0) {
            //in LOS
            health = unit.getHealth();
        }
        if (unit != null && unit.getPos().length() > 0) {
            //in Radar
            lastPos = unit.getPos();
            lastSeen = command.getCurrentFrame();
        } else if (shouldBeVisible(lastPos)) {
            //command.debug("new pos");
            AIFloat3 npos = command.areaManager.getArea(lastPos).getNearestArea(invisible).getPos();
            if (npos != null) {
                lastPos = npos;
            }
            if (isBuilding) {
                if (this instanceof FakeEnemy) {
                    command.removeFakeEnemy((FakeEnemy) this);
                } else {
                    command.unitDestroyed(unit, null);
                }
            }
        }
        if (neverSeen && unit != null && unit.getVel().length() > maxVelocity) {
            identify();
        }
        //command.mark(getPos(), getPos().toString());
        command.addSingleUpdateListener(this, frame + 40);
    }

    public void enterLOS() {
        if (!alive) {
            polledDead();
        }
        if (neverSeen) {
            health = unit.getHealth();
            unitDef = unit.getDef();
            maxRange = unit.getMaxRange();
            if (unitDef.getName().equals("cormex")) {
                command.areaManager.getNearestMex(getPos()).setEnemyMex(this);
            }
            isBuilding = unit.getDef().getSpeed() <= 0;
        }
        neverSeen = false;
    }

    public float getMetalCost() {
        if (!alive) {
            polledDead();
        }
        return getDef().getCost(command.metal);
    }

    public boolean isBuilding() {
        if (!alive) {
            polledDead();
        }
        return isBuilding;
    }

    public boolean isIdentifiedByRadar() {
        if (!alive) {
            polledDead();
        }
        return neverSeen && getDef().getSpeed() > 0.5;
    }

    public void identify() {
        if (!alive) {
            polledDead();
        }
        if (!neverSeen) {
            return;
        }
        if (unit != null) {
            maxVelocity = Math.max(unit.getVel().length(), maxVelocity);
            if (command.getEnemyUnitDefSpeedMap().ceilingEntry(unit.getVel().length() - 1e-6f) != null) {
                unitDef = command.getEnemyUnitDefSpeedMap().ceilingEntry(unit.getVel().length() - 1e-6f).getValue();
            }
        }
        if (unitDef == null) {
            unitDef = clbk.getUnitDefByName("armpw");
        }
        maxRange = 0;
        for (WeaponMount wm : unitDef.getWeaponMounts()) {
            maxRange = Math.max(wm.getWeaponDef().getRange(), maxRange);
        }
        //command.debug(unitDef.getHumanName() + " identified by Radar");
        health = unitDef.getHealth();
    }

    public void enterRadar() {

    }

    public void leaveLOS() {
    }

    public void leaveRadar() {
    }

    public void destroyed() {
        //command.mark(getPos(), "dead");
        command.debug("Enemy " + hashCode() + " has been destroyed");
        alive = false;
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
