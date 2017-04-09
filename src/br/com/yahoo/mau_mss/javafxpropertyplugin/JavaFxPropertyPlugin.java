package br.com.yahoo.mau_mss.javafxpropertyplugin;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePathScanner;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.java.source.CancellableTask;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.CompilationInfo;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.JavaSource.Phase;
import org.netbeans.api.java.source.ModificationResult;
import org.netbeans.api.java.source.Task;
import org.netbeans.api.java.source.TreeMaker;
import org.netbeans.api.java.source.WorkingCopy;
import org.netbeans.spi.editor.codegen.CodeGenerator;
import org.netbeans.spi.editor.codegen.CodeGeneratorContextProvider;
import org.openide.awt.StatusDisplayer;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

/**
 * Title: JavaFxPropertyPlugin
 * Description: Gera métodos JavaFX property pattern a partir do Alt+Insert
 * Date: March 3, 2015, 12:33:00 PM
 *
 * @author Mauricio Soares da Silva (mauricio.soares)
 * @see https://platform.netbeans.org/tutorials/nbm-code-generator.html
 * @see http://docs.oracle.com/javase/8/javafx/properties-binding-tutorial/binding.htm#JFXBD107
 */
public class JavaFxPropertyPlugin implements CodeGenerator {
  private final JTextComponent textComp;
  private List<Element> fieldElements;

  /**
   * Creates a new instance of JavaFxPropertyPlugin
   *
   * @param context containing JTextComponent and possibly other items
   * registered by {@link CodeGeneratorContextProvider}
   */
  private JavaFxPropertyPlugin(Lookup context) {
    textComp = context.lookup(JTextComponent.class);
  }

  /**
   * The name which will be inserted inside Insert Code dialog
   */
  @Override
  public String getDisplayName() {
    return "JavaFX Property Pattern...";
  }

  /**
   * This will be invoked when user chooses this Generator from Insert Code
   * dialog
   */
  @Override
  public void invoke() {
    try {
      Document doc = textComp.getDocument();
      JavaSource javaSource = JavaSource.forDocument(doc);
      if (javaSource == null) {
        StatusDisplayer.getDefault().setStatusText("It is not a Java file!");
        return;
      }

      javaSource.runUserActionTask(new Task<CompilationController>() {
        @Override
        public void run(CompilationController compilationController) throws Exception {
          compilationController.toPhase(JavaSource.Phase.ELEMENTS_RESOLVED);
          Document document = compilationController.getDocument();
          if (document == null) {
            StatusDisplayer.getDefault().setStatusText("The Java file is closed!");
            return;
          }
          StatusDisplayer.getDefault().setStatusText("The Java file is open!");
          new MemberVisitor(compilationController).scan(compilationController.getCompilationUnit(), null);
        }
      }, true);

      CancellableTask<WorkingCopy> task = new CancellableTask<WorkingCopy>() {
        @Override
        public void run(WorkingCopy workingCopy) throws IOException {
          workingCopy.toPhase(Phase.RESOLVED);
          CompilationUnitTree cut = workingCopy.getCompilationUnit();
          TreeMaker make = workingCopy.getTreeMaker();
          for (Tree typeDecl : cut.getTypeDecls()) {
            if (Tree.Kind.CLASS == typeDecl.getKind()) {
              ClassTree clazz = (ClassTree) typeDecl;
              ClassTree modifiedClazz = clazz;
              MethodTree newConstructorWithoutArgs = createConstructorWithoutArgs(make);
              modifiedClazz = make.addClassMember(modifiedClazz, newConstructorWithoutArgs);
              MethodTree newConstructor = createConstructor(make);
              modifiedClazz = make.addClassMember(modifiedClazz, newConstructor);
              for (Element element : fieldElements) {
                MethodTree newMethod = createMethodGet(make, element);
                modifiedClazz = make.addClassMember(modifiedClazz, newMethod);
                newMethod = createMethodSet(make, element);
                modifiedClazz = make.addClassMember(modifiedClazz, newMethod);
                if (hasProperty(element)) {
                  newMethod = createMethodGetProperty(make, element);
                  modifiedClazz = make.addClassMember(modifiedClazz, newMethod);
                }
              }
              workingCopy.rewrite(clazz, modifiedClazz);
            }
          }
        }

        @Override
        public void cancel() {
        }
      };
      ModificationResult result = javaSource.runModificationTask(task);
      result.commit();
    } catch (IllegalArgumentException | IOException ex) {
      Exceptions.printStackTrace(ex);
    }
  }

  /**
   * Cria um construtor como abaixo:
   * <code>
   * public JavaApplication(String firstName, Integer postalCode, LocalDate birthday) {
   *   this.firstName.set(firstName);
   *   this.postalCode.set(postalCode);
   *   this.birthday.set(birthday);
   * }
   * </code>
   *
   * @param make
   * @return
   */
  private MethodTree createConstructor(TreeMaker make) {
    ModifiersTree methodModifiers
            = make.Modifiers(Collections.<Modifier>singleton(Modifier.PUBLIC),
                    Collections.<AnnotationTree>emptyList());
    List<VariableTree> variables = getConstructorParameters(make);
    MethodTree newConstructor
            = make.Constructor(
                    methodModifiers,
                    Collections.<TypeParameterTree>emptyList(),
                    variables,
                    Collections.<ExpressionTree>emptyList(),
                    getConstructorBody());
    return newConstructor;
  }

  private List<VariableTree> getConstructorParameters(TreeMaker make) {
    List<VariableTree> variables = new ArrayList<>();
    for (Element element : fieldElements) {
      VariableTree parameter
              = make.Variable(make.Modifiers(Collections.<Modifier>emptySet(), Collections.<AnnotationTree>emptyList()),
                      getElementName(element),
                      make.Identifier(parseElementType(element)),
                      null);
      variables.add(parameter);
    }
    return variables;
  }

  private String getConstructorBody() {
    StringBuilder sb = new StringBuilder();
    sb.append("{ ");
    for (int i = 0; i < fieldElements.size(); i++) {
      Element element = fieldElements.get(i);
      if (i > 0) {
        sb.append("\n");
      }
      sb.append("this.");
      sb.append(getElementName(element));
      sb.append(" = ");
      sb.append(getSimpleProperty(element));
      if (i < fieldElements.size() - 1) {
        sb.append(";");
      }
    }
    sb.append(" }");
    return sb.toString();
  }

  private String getSimpleProperty(Element element) {
    String elementType = getElementType(element);
    elementType = removeOuterClassPackagePath(elementType);
    int indLt = elementType.indexOf('<');
    if (indLt > -1) {
      int indGt = elementType.indexOf('>');
      elementType = elementType.substring(0, indLt + 1) + elementType.substring(indGt);
    }
    if (hasProperty(element)) {
      StringBuilder sb = new StringBuilder();
      sb.append("new Simple");
      sb.append(elementType);
      sb.append("(");
      if (hasNotNullableProperty(elementType)) {
        sb.append(getElementName(element));
        sb.append(" != null ? ");
        sb.append(getElementName(element));
        sb.append(" : ");
        sb.append(getDefaultValue(elementType));
      } else {
        sb.append(getElementName(element));
      }
      sb.append(")");
      return sb.toString();
    }
    return getElementName(element);
  }

  /**
   * Cria um construtor como abaixo:
   * <code>
   * public JavaApplication(String firstName, Integer postalCode, LocalDate birthday) {
   *   this("", 0, null);
   * }
   * </code>
   *
   * @param make
   * @return
   */
  private MethodTree createConstructorWithoutArgs(TreeMaker make) {
    ModifiersTree methodModifiers
            = make.Modifiers(Collections.<Modifier>singleton(Modifier.PUBLIC),
                    Collections.<AnnotationTree>emptyList());
    MethodTree newConstructor
            = make.Constructor(
                    methodModifiers,
                    Collections.<TypeParameterTree>emptyList(),
                    Collections.<VariableTree>emptyList(),
                    Collections.<ExpressionTree>emptyList(),
                    getConstructorBodyWithotArgs());
    return newConstructor;
  }

  private String getConstructorBodyWithotArgs() {
    StringBuilder sb = new StringBuilder();
    sb.append("{ this(");
    for (int i = 0; i < fieldElements.size(); i++) {
      Element element = fieldElements.get(i);
      String value = getDefaultValue(getElementType(element));
      if (value.isEmpty()) {
        sb.append("null");
      } else {
        sb.append(value);
      }
      if (i < fieldElements.size() - 1) {
        sb.append(", ");
      }
    }
    sb.append(") }");
    return sb.toString();
  }

  private boolean hasNotNullableProperty(String elementType) {
    return (elementType.contains("BooleanProperty"))
            || (elementType.contains("DoubleProperty"))
            || (elementType.contains("FloatProperty"))
            || (elementType.contains("IntegerProperty"))
            || (elementType.contains("LongProperty"));
  }

  private String getDefaultValue(String elementType) {
    if (elementType.toLowerCase().contains("boolean")) {
      return "false";
    } else if (elementType.toLowerCase().contains("double")) {
      return "0d";
    } else if (elementType.toLowerCase().contains("float")) {
      return "0f";
    } else if (elementType.toLowerCase().contains("integer") || elementType.equals("int")) {
      return "0";
    } else if (elementType.toLowerCase().contains("long")) {
      return "0L";
    }
    return "";
  }

  /**
   * Cria um método como abaixo
   * <code>
   * public LocalDate getBirthday() {
   *   return this.birthday.getValue();
   * }
   * </code>
   */
  private MethodTree createMethodGet(TreeMaker make, Element enclosedElement) {
    Set<Modifier> modifiers = new HashSet<>();
    modifiers.add(Modifier.PUBLIC);
    modifiers.add(Modifier.FINAL);
    ModifiersTree methodModifiers = make.Modifiers(modifiers,
            Collections.<AnnotationTree>emptyList());
    MethodTree newMethod
            = make.Method(methodModifiers,
                    getAccessor(enclosedElement) + getElementNameStartingUppercase(enclosedElement),
                    make.Type(parseElementType(enclosedElement)),
                    Collections.<TypeParameterTree>emptyList(),
                    Collections.<VariableTree>emptyList(),
                    Collections.<ExpressionTree>emptyList(),
                    createMethodGetBody(enclosedElement),
                    null);
    return newMethod;
  }

  private String getAccessor(Element element) {
    if (getElementType(element).toLowerCase().contains("boolean")) {
      return "is";
    }
    return "get";
  }

  private String createMethodGetBody(Element enclosedElement) {
    StringBuilder sb = new StringBuilder();
    sb.append("{ return this.");
    sb.append(getElementName(enclosedElement));
    if (hasProperty(enclosedElement)) {
      sb.append(".getValue()");
    }
    sb.append(" }");
    return sb.toString();
  }

  /**
   * Cria um método como abaixo
   * <code>
   * public void setBirthday(LocalDate birthday) {
   *   this.birthday.setValue(birthday);
   * }
   * </code>
   */
  private MethodTree createMethodSet(TreeMaker make, Element enclosedElement) {
    Set<Modifier> modifiers = new HashSet<>();
    modifiers.add(Modifier.PUBLIC);
    modifiers.add(Modifier.FINAL);
    ModifiersTree methodModifiers = make.Modifiers(modifiers,
            Collections.<AnnotationTree>emptyList());
    VariableTree parameter
            = make.Variable(make.Modifiers(Collections.<Modifier>emptySet(), Collections.<AnnotationTree>emptyList()),
                    getElementName(enclosedElement),
                    make.Identifier(parseElementType(enclosedElement)),
                    null);
    MethodTree newMethod
            = make.Method(methodModifiers,
                    "set" + getElementNameStartingUppercase(enclosedElement),
                    make.PrimitiveType(TypeKind.VOID),
                    Collections.<TypeParameterTree>emptyList(),
                    Collections.singletonList(parameter),
                    Collections.<ExpressionTree>emptyList(),
                    createMethodSetBody(enclosedElement),
                    null);
    return newMethod;
  }

  private String createMethodSetBody(Element enclosedElement) {
    StringBuilder sb = new StringBuilder();
    sb.append("{ this.");
    sb.append(getElementName(enclosedElement));
    if (hasProperty(enclosedElement)) {
      sb.append(".setValue(");
      sb.append(getElementName(enclosedElement));
      sb.append(")");
    } else {
      sb.append(" = ");
      sb.append(getElementName(enclosedElement));
    }
    sb.append(" }");
    return sb.toString();
  }

  /**
   * Cria um método como abaixo
   * <code>
   * public ObjectProperty<LocalDate> getBirthdayProperty() {
   * return this.birthday;
   * }
   * </code>
   */
  private MethodTree createMethodGetProperty(TreeMaker make, Element enclosedElement) {
    ModifiersTree methodModifiers
            = make.Modifiers(Collections.<Modifier>singleton(Modifier.PUBLIC),
                    Collections.<AnnotationTree>emptyList());
    MethodTree newMethod
            = make.Method(methodModifiers,
                    getElementName(enclosedElement) + "Property",
                    make.Type(getElementType(enclosedElement)),
                    Collections.<TypeParameterTree>emptyList(),
                    Collections.<VariableTree>emptyList(),
                    Collections.<ExpressionTree>emptyList(),
                    "{ return this." + getElementName(enclosedElement) + " }",
                    null);
    return newMethod;
  }

  private String getElementNameStartingUppercase(Element element) {
    String particle = getElementName(element);
    return particle.substring(0, 1).toUpperCase() + particle.substring(1);
  }

  private String getElementName(Element element) {
    String particle = element.getSimpleName().toString();
    return particle;
  }

  private String parseElementType(Element element) {
    String ident = getElementType(element);
    ident = removeOuterClassPackagePath(ident);
    ident = parseObjectProperty(ident);
    int propInd = ident.indexOf("Property");
    if (propInd > -1) {
      ident = ident.substring(0, propInd);
    }
    return ident;
  }

  private String removeOuterClassPackagePath(String elementType) {
    int indLt = elementType.indexOf('<');
    int lastDot = elementType.lastIndexOf('.');
    if (indLt > -1) {
      lastDot = elementType.substring(0, indLt).lastIndexOf('.');
    }
    return elementType.substring(lastDot + 1);
  }

  private String parseObjectProperty(String elementType) {
    if (!elementType.startsWith("Object")) {
      return elementType;
    }
    int indLt = elementType.indexOf('<');
    int indGt = elementType.indexOf('>');
    String elementType2 = elementType.substring(indLt + 1, indGt);
    elementType2 = removeInnerClassPackagePath(elementType2);
    return elementType2;
  }

  private String removeInnerClassPackagePath(String elementType) {
    int lastDot = elementType.lastIndexOf('.');
    return elementType.substring(lastDot + 1);
  }

  private boolean hasProperty(Element element) {
    return getElementType(element).contains("Property");
  }

  private String getElementType(Element element) {
    return ((VariableElement) element).asType().toString();
  }

  @MimeRegistration(mimeType = "text/x-java", service = CodeGenerator.Factory.class)
  public static class Factory implements CodeGenerator.Factory {

    @Override
    public List<? extends CodeGenerator> create(Lookup context) {
      return Collections.singletonList(new JavaFxPropertyPlugin(context));
    }
  }

  private class MemberVisitor extends TreePathScanner<Void, Void> {
    private final CompilationInfo info;

    MemberVisitor(CompilationInfo info) {
      this.info = info;
    }

    @Override
    public Void visitClass(ClassTree t, Void v) {
      Element el = info.getTrees().getElement(getCurrentPath());
      if (el == null) {
        StatusDisplayer.getDefault().setStatusText("Cannot resolve class!");
        return null;
      }
      TypeElement te = (TypeElement) el;
      @SuppressWarnings("unchecked")
      List<Element> enclosedElements = (List<Element>) te.getEnclosedElements();
      fieldElements = new ArrayList<>();
      for (Element enclosedElement : enclosedElements) {
        if (enclosedElement.getKind() == ElementKind.FIELD) {
          fieldElements.add(enclosedElement);
        }
      }
      return null;
    }
  }

}
