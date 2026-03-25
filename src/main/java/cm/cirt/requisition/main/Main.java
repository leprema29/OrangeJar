/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cm.cirt.requisition.main;

import cm.cirt.requisition.metier.Utils;

/**
 *
 * @author Harry Wanki
 */
public class Main {
    public static void main(String[] args){
        Utils util = new Utils(args[0], args[1]);
        util.analyseListing();
    }
}
