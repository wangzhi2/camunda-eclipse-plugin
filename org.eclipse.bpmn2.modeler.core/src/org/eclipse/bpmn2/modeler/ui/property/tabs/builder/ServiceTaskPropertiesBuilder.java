package org.eclipse.bpmn2.modeler.ui.property.tabs.builder;

import static org.eclipse.bpmn2.modeler.ui.property.tabs.util.Events.RADIO_SELECTION_CHANGED;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.bpmn2.ServiceTask;
import org.eclipse.bpmn2.modeler.runtime.activiti.model.ModelPackage;
import org.eclipse.bpmn2.modeler.ui.property.tabs.binding.ModelRadioBinding;
import org.eclipse.bpmn2.modeler.ui.property.tabs.dialog.ClassChooserDialog;
import org.eclipse.bpmn2.modeler.ui.property.tabs.radio.Radio.RadioGroup;
import org.eclipse.bpmn2.modeler.ui.property.tabs.util.Events.RadioSelectionChanged;
import org.eclipse.bpmn2.modeler.ui.property.tabs.util.PropertyUtil;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.util.TransactionUtil;
import org.eclipse.graphiti.ui.platform.GFPropertySection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class ServiceTaskPropertiesBuilder extends AbstractPropertiesBuilder<ServiceTask> {

	private static final String[] TYPE_NAMES = new String[] { 
		"Class", "Expression", "Expression Delegate" 
	};
	
	private static final EStructuralFeature[] TYPE_FEATURES = new EStructuralFeature[] { 
		 ModelPackage.eINSTANCE.getDocumentRoot_Class(), 
		 ModelPackage.eINSTANCE.getDocumentRoot_ActExpression(), 
		 ModelPackage.eINSTANCE.getDocumentRoot_DelegateExpression()
	};
	
	private static final EStructuralFeature CLASS_FEATURE = TYPE_FEATURES[0];
	
	private RadioGroup<EStructuralFeature> radioGroup;
	private Map<EStructuralFeature, Text> featureToInputMap; 
	
	/**
	 * Creates a new factory from the given arguments
	 * 
	 * @param parent
	 * @param section
	 * @param bo
	 */
	public ServiceTaskPropertiesBuilder(Composite parent, GFPropertySection section, ServiceTask bo) {
		super(parent, section, bo);
		
		this.radioGroup = new RadioGroup<EStructuralFeature>();
		this.featureToInputMap = new HashMap<EStructuralFeature, Text>(); 
	}

	/**
	 * Creates the service task specific controls
	 */
	@Override
	public void create() {
		createServiceTaskTypeControls();
		
		PropertyUtil.createText(section, parent, "Result Variable", ModelPackage.eINSTANCE.getDocumentRoot_ResultVariableName(), bo);	
	}
	
	private void createServiceTaskTypeControls() {
		
		EStructuralFeature selected = null;
		
		for (int i = 0; i < TYPE_NAMES.length; i++) {
			String name = TYPE_NAMES[i];
			final EStructuralFeature feature = TYPE_FEATURES[i];
			
			final Text text = PropertyUtil.createRadioText(section, parent, name, feature, radioGroup, bo);
			text.setEnabled(false);
			
			featureToInputMap.put(feature, text);
			
			if (bo.eIsSet(feature)) {
				selected = feature;
			}
			
			Button radioControl = radioGroup.getRadioControl(feature);
			
			new ModelRadioBinding(bo, feature, radioControl).establish();
		}

		final Text classText = featureToInputMap.get(CLASS_FEATURE);
		addBrowseClassButton(classText);
		
//		radioGroup.select(selected, true);
		
		radioGroup.addListener(RADIO_SELECTION_CHANGED, new Listener() {
			
			@Override
			public void handleEvent(Event e) {
				RadioSelectionChanged<EStructuralFeature> event = (RadioSelectionChanged<EStructuralFeature>) e;
				
				transactionalHandleTypeChange(event.getOldSelection(), event.getNewSelection());
			}
		});
	}

	private void addBrowseClassButton(final Text classText) {

		Composite classComposite = classText.getParent();
		final Button radioButton = (Button) classComposite.getChildren()[1];
		
		// monkey patching for the win!
		
		final Button btnClassSelect = new Button(classComposite, SWT.NONE);
		btnClassSelect.setText("Choose Class");
		btnClassSelect.setEnabled(radioButton.getSelection());
		
		// customize layout data for text field
		FormData textFormData = (FormData) classText.getLayoutData();
		// make space for button
		textFormData.right = new FormAttachment(100, -90);
		
		FormData btnSelectLayoutData = new FormData();
		btnSelectLayoutData.left = new FormAttachment(classText, 0);
		btnSelectLayoutData.top = new FormAttachment(classText, 0, SWT.CENTER);
		
		classText.setLayoutData(textFormData);
		btnClassSelect.setLayoutData(btnSelectLayoutData);
		
		radioButton.addListener(SWT.Selection, new Listener() {
			
			@Override
			public void handleEvent(Event event) {
				boolean selected = radioButton.getSelection();
				btnClassSelect.setEnabled(selected);
			}
		});
		
		btnClassSelect.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent evt) {
				Shell shell = classText.getShell();
				ClassChooserDialog dialog = new ClassChooserDialog(shell);
				
				// interrupting operation
				String clsName = dialog.chooseClass();
				
				if (clsName != null) {
					classText.setText(clsName);
				}
			}
		});
	}

	protected void transactionalHandleTypeChange(EStructuralFeature oldType, EStructuralFeature newType) {
		TransactionalEditingDomain domain = getTransactionalEditingDomain();
		domain.getCommandStack().execute(new ToggleFeaturesCommand(domain, bo, oldType, newType));
	}

	private TransactionalEditingDomain getTransactionalEditingDomain() {
		return TransactionUtil.getEditingDomain(bo);
	}

	// model radio binding ////////////////////////////////
	

	private class ToggleFeaturesCommand extends RecordingCommand {
		
		private EObject object;
		
		private EStructuralFeature oldFeature;
		private EStructuralFeature newFeature;
		
		public ToggleFeaturesCommand(TransactionalEditingDomain domain, EObject object, EStructuralFeature oldFeature, EStructuralFeature newFeature) {
			super(domain);
			
			this.object = object;
			
			this.oldFeature = oldFeature;
			this.newFeature = newFeature;
		}
		
		@Override
		protected void doExecute() {
			if (oldFeature != null) {
				object.eUnset(oldFeature);
			}
			object.eSet(newFeature, "");
		}
	}
}
