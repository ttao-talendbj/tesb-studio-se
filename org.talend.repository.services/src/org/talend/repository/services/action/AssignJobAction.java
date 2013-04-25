package org.talend.repository.services.action;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.emf.common.util.EList;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.PlatformUI;
import org.talend.commons.exception.ExceptionHandler;
import org.talend.commons.exception.PersistenceException;
import org.talend.commons.ui.runtime.image.ECoreImage;
import org.talend.commons.ui.runtime.image.ImageProvider;
import org.talend.core.CorePlugin;
import org.talend.core.model.process.EParameterFieldType;
import org.talend.core.model.process.IElementParameter;
import org.talend.core.model.process.INode;
import org.talend.core.model.process.IProcess;
import org.talend.core.model.process.IProcess2;
import org.talend.core.model.properties.ConnectionItem;
import org.talend.core.model.properties.Item;
import org.talend.core.model.properties.ProcessItem;
import org.talend.core.model.properties.Property;
import org.talend.core.model.repository.ERepositoryObjectType;
import org.talend.core.model.repository.IRepositoryViewObject;
import org.talend.core.model.repository.RepositoryManager;
import org.talend.core.repository.model.ProxyRepositoryFactory;
import org.talend.core.repository.ui.actions.metadata.AbstractCreateAction;
import org.talend.designer.core.DesignerPlugin;
import org.talend.designer.core.IDesignerCoreService;
import org.talend.designer.core.model.components.EParameterName;
import org.talend.designer.core.model.components.EmfComponent;
import org.talend.designer.core.model.utils.emf.talendfile.ElementParameterType;
import org.talend.designer.core.model.utils.emf.talendfile.NodeType;
import org.talend.designer.core.model.utils.emf.talendfile.ProcessType;
import org.talend.designer.core.ui.editor.cmd.ChangeValuesFromRepository;
import org.talend.repository.RepositoryPlugin;
import org.talend.repository.model.ERepositoryStatus;
import org.talend.repository.model.IProxyRepositoryFactory;
import org.talend.repository.model.IRepositoryNode;
import org.talend.repository.model.IRepositoryNode.EProperties;
import org.talend.repository.model.ProjectRepositoryNode;
import org.talend.repository.model.RepositoryNode;
import org.talend.repository.services.Messages;
import org.talend.repository.services.model.services.ServiceConnection;
import org.talend.repository.services.model.services.ServiceItem;
import org.talend.repository.services.model.services.ServiceOperation;
import org.talend.repository.services.model.services.ServicePort;
import org.talend.repository.services.utils.OperationRepositoryObject;
import org.talend.repository.services.utils.PortRepositoryObject;
import org.talend.repository.services.utils.WSDLUtils;
import org.talend.repository.ui.dialog.RepositoryReviewDialog;

public class AssignJobAction extends AbstractCreateAction {

    private String createLabel = "Assign Job";

    private ERepositoryObjectType currentNodeType;

    public AssignJobAction() {
        super();

        this.setText(createLabel);
        this.setToolTipText(createLabel);

        this.setImageDescriptor(ImageProvider.getImageDesc(ECoreImage.PROCESS_ICON));

        currentNodeType = ERepositoryObjectType.SERVICESOPERATION;
    }

    public AssignJobAction(RepositoryNode operationNode) {
        this();
        repositoryNode = operationNode;
    }

    public AssignJobAction(boolean isToolbar) {
        this();
        setToolbar(isToolbar);

        this.setText(createLabel);
        this.setToolTipText(createLabel);

        this.setImageDescriptor(ImageProvider.getImageDesc(ECoreImage.PROCESS_ICON));
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.talend.core.repository.ui.actions.metadata.AbstractCreateAction#init(org.talend.repository.model.RepositoryNode
     * )
     */
    @Override
    protected void init(RepositoryNode node) {
        ERepositoryObjectType nodeType = (ERepositoryObjectType) node.getProperties(EProperties.CONTENT_TYPE);
        if (!currentNodeType.equals(nodeType)) {
            return;
        }
        this.setText(createLabel);
        this.setImageDescriptor(ImageProvider.getImageDesc(ECoreImage.PROCESS_ICON));
        
        IProxyRepositoryFactory proxyFactory = DesignerPlugin.getDefault().getProxyRepositoryFactory();
        try {
			proxyFactory.updateLockStatus();
		} catch (PersistenceException e) {
			e.printStackTrace();
		}
        ERepositoryStatus status = proxyFactory.getStatus(node.getObject());
        if(!status.isEditable() && !status.isPotentiallyEditable()){
			setEnabled(false);
		}else{
			setEnabled(true);
		}
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.talend.repository.ui.actions.AContextualAction#doRun()
     */
    @Override
    protected void doRun() {
        if (repositoryNode == null) {
            repositoryNode = getCurrentRepositoryNode();
        }
        if (isToolbar()) {
            if (repositoryNode != null && repositoryNode.getContentType() != currentNodeType) {
                repositoryNode = null;
            }
            if (repositoryNode == null) {
                repositoryNode = getRepositoryNodeForDefault(currentNodeType);
            }
        }
        RepositoryReviewDialog dialog = new RepositoryReviewDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow()
                .getShell(), ERepositoryObjectType.PROCESS, "");
        List<String> jobIDList = getAllReferenceJobId(repositoryNode);
        dialog.setJobIDList(jobIDList);
        if (dialog.open() == RepositoryReviewDialog.OK) {
            changeOldJob();
            assign(dialog.getResult());
        }
    }

    public void changeOldJob() {
        String operationName = repositoryNode.getObject().getLabel();
        String portName = repositoryNode.getParent().getObject().getLabel();
        ServiceItem serviceItem = (ServiceItem) repositoryNode.getParent().getParent().getObject().getProperty().getItem();
        List<ServicePort> listPort = ((ServiceConnection) serviceItem.getConnection()).getServicePort();
        String oldJobID = null;
        for (ServicePort port : listPort) {
            if (port.getName().equals(portName)) {
                List<ServiceOperation> listOperation = port.getServiceOperation();
                for (ServiceOperation operation : listOperation) {
                    if (operation.getLabel().equals(operationName)) {
                        oldJobID = operation.getReferenceJobId();
                        break;
                    }
                }
                break;
            }
        }
        IProxyRepositoryFactory factory = ProxyRepositoryFactory.getInstance();
        try {
            if (oldJobID != null) {
                IRepositoryViewObject object = factory.getLastVersion(oldJobID);
                Item item = object.getProperty().getItem();
                ProcessItem processItem = (ProcessItem) item;
                //
                IDesignerCoreService service = CorePlugin.getDefault().getDesignerCoreService();
                boolean foundInOpen = false;
                IProcess2 process = null;
                IEditorReference[] reference = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
                        .getEditorReferences();
                List<IProcess2> processes = RepositoryPlugin.getDefault().getDesignerCoreService().getOpenedProcess(reference);
                for (IProcess2 processOpen : processes) {
                    if (processOpen.getProperty().getItem() == processItem) {
                        foundInOpen = true;
                        process = processOpen;
                        break;
                    }
                }
                if (!foundInOpen) {
                    IProcess proc = service.getProcessFromProcessItem(processItem);
                    if (proc instanceof IProcess2) {
                        process = (IProcess2) proc;
                    }
                }

                if (process != null) {
                    List<? extends INode> nodelist = process.getGraphicalNodes();
                    for (INode node : nodelist) {
                        if (node.getComponent().getName().equals("tESBProviderRequest")) {
                            repositoryChangeToBuildIn(repositoryNode, node);
                        }
                    }
                    processItem.setProcess(process.saveXmlFile());

                    try {
                        factory.save(processItem);
                    } catch (PersistenceException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (PersistenceException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean  assign(IRepositoryNode jobNode) {
        if (jobNode == null) {
            return false;
        }
        IRepositoryViewObject repositoryObject = jobNode.getObject();
        final Item item = repositoryObject.getProperty().getItem();
        // judge the job whether had T_ESB_PROVIDER_REQUEST
        ProcessItem processItem = (ProcessItem) item;
        ProcessType processType = processItem.getProcess();
        EList nodeList = processType.getNode();
        boolean hadEsbRequestComponent = false;
        for (Object obj : nodeList) {
            NodeType node = (NodeType) obj;
            if (CreateNewJobAction.T_ESB_PROVIDER_REQUEST.equals(node.getComponentName())) {
                hadEsbRequestComponent = true;
            }
        }
        if (!hadEsbRequestComponent) {
            MessageDialog.openWarning(Display.getCurrent().getActiveShell(), Messages.AssignJobAction_WarningTitle,
                    Messages.AssignJobAction_WarningMessage);
            return false;
        }
        try {
            String jobID = item.getProperty().getId();
            String jobName = item.getProperty().getLabel();
            String operationName = repositoryNode.getObject().getLabel();
            String portName = repositoryNode.getParent().getObject().getLabel();
            ServiceItem serviceItem = (ServiceItem) repositoryNode.getParent().getParent().getObject().getProperty().getItem();

            List<ServicePort> listPort = ((ServiceConnection) serviceItem.getConnection()).getServicePort();
            for (ServicePort port : listPort) {
                if (port.getName().equals(portName)) {
                    List<ServiceOperation> listOperation = port.getServiceOperation();
                    for (ServiceOperation operation : listOperation) {
                        if (operation.getLabel().equals(operationName)) {
                            // should not change the job name
                            // String jobNewName = port.getName() + "_" + operation.getName();
                            // if (resetJobname(item, jobNewName)) {
                            // jobName = jobNewName;
                            // }
                            operation.setReferenceJobId(jobID);
                            operation.setLabel(operation.getName() + "-" + jobName);
                            break;
                        }
                    }
                    break;
                }
            }

            IFile wsdlPath = WSDLUtils.getWsdlFile(serviceItem);
            Map<String, String> serviceParameters = WSDLUtils.getServiceOperationParameters(wsdlPath,
                    ((OperationRepositoryObject) repositoryNode.getObject()).getName(), portName);

            for (Object obj : nodeList) {
                NodeType node = (NodeType) obj;
                if (CreateNewJobAction.T_ESB_PROVIDER_REQUEST.equals(node.getComponentName())) {
                    EList parameters = node.getElementParameter();
                    for (Object paramObj : parameters) {
                        ElementParameterType param = (ElementParameterType) paramObj;
                        String name = param.getName();
                        if (serviceParameters.containsKey(name)) {
                            param.setValue(serviceParameters.get(name));
                        }
                    }
                    break;
                }
            }

            IProxyRepositoryFactory factory = ProxyRepositoryFactory.getInstance();

            try {
                factory.save(processItem);
            } catch (PersistenceException e) {
                e.printStackTrace();
            }

            IDesignerCoreService service = CorePlugin.getDefault().getDesignerCoreService();
            boolean foundInOpen = false;

            IProcess2 process = null;
            IEditorReference[] reference = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
                    .getEditorReferences();
            List<IProcess2> processes = RepositoryPlugin.getDefault().getDesignerCoreService().getOpenedProcess(reference);
            for (IProcess2 processOpen : processes) {
                if (processOpen.getProperty().getItem() == processItem) {
                    foundInOpen = true;
                    process = processOpen;
                    break;
                }
            }
            if (!foundInOpen) {
                IProcess proc = service.getProcessFromProcessItem(processItem);
                if (proc instanceof IProcess2) {
                    process = (IProcess2) proc;
                }
            }

            if (process != null) {
                List<? extends INode> nodelist = process.getGraphicalNodes();
                for (INode node : nodelist) {
                    if (node.getComponent().getName().equals("tESBProviderRequest")) {
                        repositoryChange(repositoryNode, node);
                    }
                }
                processItem.setProcess(process.saveXmlFile());

                try {
                    factory.save(processItem);
                } catch (PersistenceException e) {
                    e.printStackTrace();
                }
            }

            try {
                factory.save(serviceItem);
            } catch (PersistenceException e) {
                e.printStackTrace();
            }
            RepositoryManager.refreshSavedNode(repositoryNode);
            return true;
        } catch (Exception e) {
            ExceptionHandler.process(e);
        }
        return false;
    }

    public Class getClassForDoubleClick() {
        try {
            RepositoryNode repositoryNode = getCurrentRepositoryNode();
            return (OpenJobAction.getReferenceJobId(repositoryNode) == null) ? ServiceOperation.class : Object.class;
        } catch (Exception e) {
            // do nothing just return default
        }
        return ServiceOperation.class;
    }

    private RepositoryNode getTopParent(RepositoryNode repositoryNode) {
        repositoryNode = repositoryNode.getParent();
        if (repositoryNode.getParent() instanceof ProjectRepositoryNode) {
            return repositoryNode;
        }
        return getTopParent(repositoryNode);
    }

    public List<String> getAllReferenceJobId(){
    	return getAllReferenceJobId(getCurrentRepositoryNode());
    }
    
    private List<String> getAllReferenceJobId(RepositoryNode repositoryNode) {
        repositoryNode = getTopParent(repositoryNode);
        List<IRepositoryNode> nodeList = repositoryNode.getChildren();
        List<ServiceOperation> operaList = new ArrayList<ServiceOperation>();
        List<String> jobIDList = new ArrayList<String>();
        for (IRepositoryNode node : nodeList) {
            if (node.getObject().getProperty().getItem() instanceof ServiceItem) {
                ServiceItem item = (ServiceItem) node.getObject().getProperty().getItem();
                ServiceConnection conn = (ServiceConnection) item.getConnection();
                EList<ServicePort> portList = conn.getServicePort();
                for (ServicePort port : portList) {
                    operaList.addAll(port.getServiceOperation());
                }
            }
        }
        for (ServiceOperation operation : operaList) {
            String jobID = operation.getReferenceJobId();
            if (jobID != null) {
                jobIDList.add(jobID);
            }
        }
        return jobIDList;
    }

    private void repositoryChange(RepositoryNode repNode, INode node) {
        IElementParameter param = node.getElementParameterFromField(EParameterFieldType.PROPERTY_TYPE);
        ConnectionItem connectionItem = (ConnectionItem) repNode.getObject().getProperty().getItem();
        if (param != null) {
            param.getChildParameters().get(EParameterName.PROPERTY_TYPE.getName()).setValue(EmfComponent.REPOSITORY);
            String serviceId = connectionItem.getProperty().getId();
            String portId = ((PortRepositoryObject) repNode.getParent().getObject()).getId();
            String operationId = ((OperationRepositoryObject) repNode.getObject()).getId();
            ChangeValuesFromRepository command2 = new ChangeValuesFromRepository(
                    node,
                    connectionItem.getConnection(),
                    param.getName() + ":" + EParameterName.REPOSITORY_PROPERTY_TYPE.getName(), serviceId + " - " + portId + " - " + operationId); //$NON-NLS-1$
            command2.execute();
        }
    }

    private void repositoryChangeToBuildIn(RepositoryNode repNode, INode node) {
        IElementParameter param = node.getElementParameterFromField(EParameterFieldType.PROPERTY_TYPE);
        ConnectionItem connectionItem = (ConnectionItem) repNode.getObject().getProperty().getItem();
        if (param != null) {
            param.getChildParameters().get(EParameterName.PROPERTY_TYPE.getName()).setValue(EmfComponent.BUILTIN);
            ChangeValuesFromRepository command2 = new ChangeValuesFromRepository(node, connectionItem.getConnection(),
                    param.getName() + ":" + EParameterName.PROPERTY_TYPE.getName(), EmfComponent.BUILTIN); //$NON-NLS-1$
            command2.execute();
            command2 = new ChangeValuesFromRepository(node, connectionItem.getConnection(), param.getName()
                    + ":" + EParameterName.REPOSITORY_PROPERTY_TYPE.getName(), ""); //$NON-NLS-1$
            command2.execute();
        }
    }

    private boolean resetJobname(Item processItem, String jobNewName) {
        IProxyRepositoryFactory factory = ProxyRepositoryFactory.getInstance();
        boolean avaliable;
        try {
            avaliable = factory.isNameAvailable(processItem, jobNewName);
            if (avaliable) {
                Property property = processItem.getProperty();
                property.setLabel(jobNewName);
                return true;
            } else {
                MessageBox box = new MessageBox(Display.getCurrent().getActiveShell(), SWT.ICON_WARNING | SWT.OK);
                box.setText("Jobname Error");
                box.setMessage("Label" + " " + jobNewName + " " + "is already in use!");
                if (box.open() == SWT.OK) {
                    return false;
                }
            }
        } catch (PersistenceException e) {
            ExceptionHandler.process(e);
        }
        return false;
    }

}
