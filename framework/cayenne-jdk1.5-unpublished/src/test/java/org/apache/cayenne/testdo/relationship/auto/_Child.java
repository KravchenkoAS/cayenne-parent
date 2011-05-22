package org.apache.cayenne.testdo.relationship.auto;

import org.apache.cayenne.CayenneDataObject;
import org.apache.cayenne.testdo.relationship.Master;

/**
 * Class _Child was generated by Cayenne.
 * It is probably a good idea to avoid changing this class manually,
 * since it may be overwritten next time code is regenerated.
 * If you need to make any customizations, please use subclass.
 */
public abstract class _Child extends CayenneDataObject {

    public static final String MASTER_PROPERTY = "master";

    public static final String ID_PK_COLUMN = "ID";

    public void setMaster(Master master) {
        setToOneTarget("master", master, true);
    }

    public Master getMaster() {
        return (Master)readProperty("master");
    }


}
