package io.onedev.server.search.entity.pullrequest;

import javax.annotation.Nullable;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;

import io.onedev.server.model.PullRequest;
import io.onedev.server.model.PullRequestComment;
import io.onedev.server.model.User;
import io.onedev.server.util.ProjectScope;
import io.onedev.server.util.criteria.Criteria;

public class CommentedByUserCriteria extends Criteria<PullRequest> {

	private static final long serialVersionUID = 1L;

	private final User user;
	
	public CommentedByUserCriteria(User user) {
		this.user = user;
	}
	
	@Override
	public Predicate getPredicate(@Nullable ProjectScope projectScope, CriteriaQuery<?> query, From<PullRequest, PullRequest> from, CriteriaBuilder builder) {
		Subquery<PullRequestComment> commentQuery = query.subquery(PullRequestComment.class);
		Root<PullRequestComment> comment = commentQuery.from(PullRequestComment.class);
		commentQuery.select(comment);
		commentQuery.where(builder.and(
				builder.equal(comment.get(PullRequestComment.PROP_REQUEST), from),
				builder.equal(comment.get(PullRequestComment.PROP_USER), user)));
		return builder.exists(commentQuery);
	}

	@Override
	public boolean matches(PullRequest request) {
		return request.getComments().stream().anyMatch(it->it.getUser().equals(user));
	}

	@Override
	public String toStringWithoutParens() {
		return PullRequestQuery.getRuleName(PullRequestQueryLexer.CommentedBy) + " " + quote(user.getName());
	}

}
