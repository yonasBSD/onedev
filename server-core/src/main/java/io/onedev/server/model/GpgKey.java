package io.onedev.server.model;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.onedev.server.annotation.Editable;
import io.onedev.server.model.support.BaseGpgKey;
import io.onedev.server.rest.annotation.Immutable;

@Editable
@Entity
@Table(indexes={@Index(columnList="keyId")})
@Cache(usage=CacheConcurrencyStrategy.READ_WRITE)
public class GpgKey extends BaseGpgKey {
    
    private static final long serialVersionUID = 1L;
    
    public static final String PROP_KEY_ID = "keyId";
    
    @JsonIgnore
    @Column(unique=true)
    private long keyId;

    @JsonIgnore
    @Column(nullable=false)
    private Date createdAt;
    
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(nullable=false)
    @Immutable
    private User owner;
    
    public long getKeyId() {
        return keyId;
    }

    public void setKeyId(long keyId) {
        this.keyId = keyId;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
    
}
