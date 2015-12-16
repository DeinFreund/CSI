/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.helpers;

import com.springrts.ai.oo.clb.UnitDef;
import zkcbai.Command;
import zkcbai.helpers.ZoneManager.Area;
import zkcbai.unitHandlers.units.Enemy;

/**
 *
 * @author User
 */
public class CounterAvoidance implements CostSupplier {

        UnitDef def;
        Command command;
        float mindist;

        public CounterAvoidance(UnitDef ud, Command command) {
            this(ud,command,0);
        }
        
        public CounterAvoidance(UnitDef ud, Command command, float decloakDist) {
            this.def = ud;
            this.command = command;
            this.mindist = decloakDist;
        }

        @Override
        public float getCost(Area pos) {
            float danger = 0;
            for (Enemy e : command.getEnemyUnits(false)) {
                if (e.distanceTo(pos.getPos()) < e.getMaxRange() * 1.4 && command.killCounter.getEfficiency(e.getDef(), def) > 3) {
                    danger += e.getMetalCost();
                }
                if (e.distanceTo(pos.getPos()) < mindist) {
                    danger += e.getMetalCost() * 1.5;
                }
            }
            return danger * 10 + command.defenseManager.getGeneralDanger(pos.getPos());
        }

    }