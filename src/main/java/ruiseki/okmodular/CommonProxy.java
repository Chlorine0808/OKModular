package ruiseki.okmodular;

import ruiseki.omoshiroikamo.core.init.ModBase;
import ruiseki.omoshiroikamo.core.proxy.CommonProxyComponent;

public class CommonProxy extends CommonProxyComponent {

    public CommonProxy() {}

    @Override
    public ModBase getMod() {
        return OKModular.instance;
    }
}
