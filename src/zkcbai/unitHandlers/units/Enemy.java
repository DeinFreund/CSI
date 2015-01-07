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
import zkcbai.helpers.ZoneManager;

/**
 *
 * @author User
 */
public class Enemy implements UpdateListener {

    private Unit unit;
    private int unitId;
    private OOAICallback clbk;
    private Command command;
    private UnitDef unitDef;
    private boolean neverSeen = true;
    private float maxVelocity = 0.5f;
    private AIFloat3 lastPos;
    private boolean alive = true;
    private float maxRange = 0;
    private boolean isBuilding;
    private int lastSeen = 0;

    public Enemy(Unit u, Command cmd, OOAICallback clbk) {
        cmd.debug("constructing");
        unit = u;
        unitId = u.getUnitId();
        this.clbk = clbk;
        command = cmd;
        unitDef = clbk.getUnitDefByName("corllt");
        lastPos = u.getPos();
        isBuilding = false;
        lastSeen = cmd.getCurrentFrame();
        cmd.debug("adding listener");
        cmd.addSingleUpdateListener(this, cmd.getCurrentFrame() + 40);

        cmd.debug("constructed");
    }

    @Deprecated
    public boolean isAlive() {
        return alive;
    }

    public AIFloat3 getPos() {
        if (unit.getPos().length() > 0) {
            return unit.getPos();
        }
        return new AIFloat3(lastPos);
    }

    public UnitDef getDef() {
        if (unit.getDef() != null) {
            return unit.getDef();
        }
        return unitDef;
    }

    public Unit getUnit() {
        return unit;
    }

    public float getMaxRange() {
        return maxRange;
    }

    public float distanceTo(AIFloat3 trg) {
        AIFloat3 pos = new AIFloat3(getPos());
        pos.sub(trg);
        pos.y = 0;
        return pos.length();
    }

    public boolean shouldBeVisible(AIFloat3 pos) {
        if (getDef().getCloakCost() <= 0) {
            return command.radarManager.isInRadar(pos) || command.losManager.isInLos(pos);
        } else {
            return !clbk.getFriendlyUnitsIn(pos, getDef().getDecloakDistance()).isEmpty();
        }
    }

    private final AreaChecker invisible = new AreaChecker() {

        @Override
        public boolean checkArea(ZoneManager.Area a) {
            return !shouldBeVisible(a.getPos());
        }

    };
    
    public int timeSinceLastSeen(){
        return command.getCurrentFrame() - lastSeen;
    }

    @Override
    public void update(int frame) {
        if (unit.getPos().length() > 0) {
            lastPos = unit.getPos();
            lastSeen = command.getCurrentFrame();
        } else if (shouldBeVisible(lastPos)) {
            //command.debug("new pos");
            AIFloat3 npos = command.areaManager.getArea(lastPos).getNearestArea(invisible).getPos();
            if (npos != null) lastPos = npos;
            if (isBuilding) command.unitDestroyed(unit, null);
        }
        //command.mark(getPos(), getPos().toString());
        command.addSingleUpdateListener(this, frame + 40);
    }

    public void enterLOS() {
        if (neverSeen) {
            unitDef = unit.getDef();
            maxRange = unit.getMaxRange();
            if (unitDef.getName().equals("cormex")) {
                command.areaManager.getNearestMex(getPos()).setEnemyMex(this);
            }
            isBuilding = unit.getDef().getSpeed() <= 0;
        }
        neverSeen = false;
    }
    
    public boolean isBuilding(){
        return isBuilding;
    }
    
    public boolean isIdentifiedByRadar(){
        return neverSeen && getDef().getSpeed() > 0.5;
    }

    public void identify() {
        if (!neverSeen) {
            return;
        }
        maxVelocity = Math.max(unit.getVel().length(), maxVelocity);
        if (command.getEnemyUnitDefSpeedMap().ceilingEntry(unit.getVel().length()) == null) {
            unitDef = clbk.getUnitDefByName("armpw");
        } else {
            unitDef = command.getEnemyUnitDefSpeedMap().ceilingEntry(unit.getVel().length()).getValue();
        }
        maxRange = 0;
        for (WeaponMount wm : unitDef.getWeaponMounts()) {
            maxRange = Math.max(wm.getWeaponDef().getRange(), maxRange);
        }
        command.debug(unitDef.getHumanName() + " identified by Radar");
    }

    public void enterRadar() {
        if (neverSeen && unit.getVel().length() > maxVelocity) {
            identify();
        }
    }

    public void leaveLOS() {
    }

    public void leaveRadar() {
    }

    public void destroyed() {
        //command.mark(getPos(), "dead");
        alive = false;
    }

    public boolean equals(Enemy e) {
        if (e== null) return false;
        return unit.getUnitId() == e.getUnit().getUnitId();
    }
}
