
package nl.tjeb.XTST;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.SequenceType;


public class LineNumbers extends ExtensionFunctionDefinition {

  @Override
  public SequenceType[] getArgumentTypes() {
    return new SequenceType[0];
  }

  @Override
  public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {
    return SequenceType.SINGLE_INTEGER;
  }

  @Override
  public StructuredQName getFunctionQName() {
    return new StructuredQName("tjeb-extensions",
        "http://tjeb.nl/xml/extensions/", "linenumbers");
  }

  @Override
  public ExtensionFunctionCall makeCallExpression() {
    return new ExtensionFunctionCall() {

      @Override
      public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        int line = 1;
        Item item = context.getContextItem();
        if (item instanceof net.sf.saxon.om.NodeInfo) {
          net.sf.saxon.om.NodeInfo nodeInfo = (net.sf.saxon.om.NodeInfo) item;
          line = nodeInfo.getLineNumber();
        }
        return Int64Value.makeIntegerValue(line);
      }
    };
  }
}
