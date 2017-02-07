/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers.squads;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Unit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import zkcbai.Command;
import zkcbai.unitHandlers.FighterHandler;
import zkcbai.unitHandlers.UnitHandler;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.AIUnit.UnitType;
import zkcbai.unitHandlers.units.Enemy;
import zkcbai.utility.Point;
import zkcbai.utility.SmallestEnclosingCircle;

/**
 *
 * @author User
 */
public abstract class SquadHandler extends UnitHandler{
    //not exactly a Task but really close

    protected Set<AIUnit> units = new HashSet();
    protected OOAICallback clbk;
    protected Command command;
    protected FighterHandler fighterHandler;

    public SquadHandler(FighterHandler fighterHandler, Command command, OOAICallback callback) {
        super(command, callback);
        if (fighterHandler == null) throw new AssertionError("inb4 nullpointer");
        if (command == null) throw new AssertionError("inb4 nullpointer");
        if (callback == null) throw new AssertionError("inb4 nullpointer");
        this.command = command;
        this.clbk = callback;
        this.fighterHandler = fighterHandler;
    }

    public AIFloat3 getPos() {
        List<Point> positions = new ArrayList();
        int c = 0;
        for (AIUnit au : units) {
            positions.add(new Point(au.getPos().x, au.getPos().z));
        }
        Point p = SmallestEnclosingCircle.makeCircle(positions).c;
        AIFloat3 pos = new AIFloat3((float) p.x, 0, (float) p.y);
        pos.y = clbk.getMap().getElevationAt(pos.x, pos.z);
        return pos;
    }

    /**
     *
     * @param u
     * @return time to unite with aiUnit u in frames(is incorrect)
     */
    public float timeTo(AIUnit u) {
        return u.distanceTo(getPos()) / u.getUnit().getDef().getSpeed();
    }
    
    public abstract UnitType getType();

    public float distanceTo(AIFloat3 trg) {
        AIFloat3 pos = new AIFloat3(getPos());
        pos.sub(trg);
        return pos.length();
    }

    @Override
    public void addUnit(AIUnit u) {
        units.add(u);
        u.assignAIUnitHandler(this);
    }

    @Override
    public Collection<AIUnit> getUnits(){
        return new ArrayList(units);
    }
    
    @Override
    public void removeUnit(AIUnit u) {
        if (!units.contains(u)) return;
        u.assignAIUnitHandler(fighterHandler);
        units.remove(u);
        if (units.isEmpty()) {
            fighterHandler.squadDestroyed(this);
            disband();
        }
    }

    public int size() {
        return units.size();
    }

    public float getMetalCost() {
        float res = 0;
        for (AIUnit u : units) {
            res += u.getUnit().getDef().getCost(command.metal);
        }
        return res;
    }

    public Set<AIUnit> disband() {
        Set<AIUnit> res = units;
        units = new HashSet();
        fighterHandler.squadDestroyed(this);
        return res;

    }



    @Override
    public void unitDestroyed(AIUnit u, Enemy e) {
        removeUnit(u);
    }

    @Override
    public void unitDestroyed(Enemy e, AIUnit killer) {
    }
    
    @Override
    public AIUnit addUnit(Unit u) {
        throw new RuntimeException("Tried to add a raw unit to a SquadHandler");
    }



}
