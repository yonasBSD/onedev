package io.onedev.server.web.component.revision;

import static io.onedev.server.web.translation.Translation._T;

import javax.annotation.Nullable;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.eclipse.jgit.lib.ObjectId;
import org.unbescape.html.HtmlEscape;

import io.onedev.server.git.GitUtils;
import io.onedev.server.model.Project;
import io.onedev.server.util.ProjectAndRevision;
import io.onedev.server.web.asset.icon.IconScope;
import io.onedev.server.web.component.floating.FloatingPanel;
import io.onedev.server.web.component.link.DropdownLink;
import io.onedev.server.web.component.modal.ModalPanel;
import io.onedev.server.web.component.svg.SpriteImage;

public abstract class RevisionPicker extends DropdownLink {

	private final IModel<Project> projectModel;
	
	private String revision;
	
	private final boolean canCreateRef;
	
	public RevisionPicker(String id, IModel<Project> projectModel, String revision, boolean canCreateRef) {
		super(id);
		
		this.projectModel = projectModel;
		this.revision = revision;
		this.canCreateRef = canCreateRef;
	}
	
	public RevisionPicker(String id, IModel<Project> projectModel, String revision) {
		this(id, projectModel, revision, false);
	}

	@Override
	protected Component newContent(String id, FloatingPanel dropdown) {
		if (projectModel.getObject() != null) {
			return new RevisionSelector(id, projectModel, revision, canCreateRef) {
	
				@Override
				protected void onSelect(AjaxRequestTarget target, String revision) {
					dropdown.close();
					RevisionPicker.this.revision = revision;
					target.add(RevisionPicker.this);
					
					RevisionPicker.this.onSelect(target, revision);
				}
	
				@Override
				protected void onModalOpened(AjaxRequestTarget target, ModalPanel modal) {
					super.onModalOpened(target, modal);
					dropdown.close();
				}
	
				@Override
				protected String getRevisionUrl(String revision) {
					return RevisionPicker.this.getRevisionUrl(revision);
				}
				
			};
		} else {
			return new Label(id, _T("Project not specified yet")).add(AttributeAppender.append("class", "m-3 text-danger font-italic"));
		}
	}

	@Override
	protected void onInitialize() {
		super.onInitialize();

		setEscapeModelStrings(false);
	}

	@Override
	public IModel<?> getBody() {
		if (projectModel.getObject() != null) {
			String icon;
			String label;
			if ("main".equals(revision)) { // default to use main branch when project is empty
				label = "main";
				icon = "branch";
			} else if (revision != null) {
				ProjectAndRevision projectAndRevision = new ProjectAndRevision(projectModel.getObject(), revision);
				label = projectAndRevision.getBranch();
				if (label != null) {
					icon = "branch";
				} else {
					label = projectAndRevision.getTag();
					if (label != null) {
						icon = "tag";
					} else {
						label = revision;
						if (ObjectId.isId(label))
							label = GitUtils.abbreviateSHA(label);
						icon = "commit";
					}
				} 
				label = HtmlEscape.escapeHtml5(label);
			} else {
				label = _T("Choose Revision");
				icon = "";
			}
			
			return Model.of(String.format(""
					+ "<span class='revision-picker text-nowrap'>"
					+ "  %s"
					+ "  <span>%s</span>"
					+ "  <svg class='icon rotate-90'><use xlink:href='%s'/></svg>"
					+ "</span>", 
					icon.length()!=0?"<svg class='icon'><use xlink:href='" + SpriteImage.getVersionedHref(IconScope.class, icon) + "'/></svg>":"", 
					label, 
					SpriteImage.getVersionedHref(IconScope.class, "arrow")));
		} else {
			return Model.of("<i>" + _T("Select project first") + "</i>");
		}
	}

	@Override
	protected void onDetach() {
		projectModel.detach();
		super.onDetach();
	}

	protected abstract void onSelect(AjaxRequestTarget target, String revision);
	
	@Nullable
	protected String getRevisionUrl(String revision) {
		return null;
	}
	
}
