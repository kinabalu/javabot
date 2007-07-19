package javabot.dao.impl;

import java.util.Date;
import java.util.List;
import javax.persistence.NoResultException;
import javax.persistence.Query;

import javabot.dao.AbstractDaoHibernate;
import javabot.dao.ChangeDao;
import javabot.dao.FactoidDao;
import javabot.dao.util.QueryParam;
import javabot.model.Factoid;

public class FactoidDaoHibernate extends AbstractDaoHibernate<Factoid> implements FactoidDao {
    private ChangeDao changeDao;

    public FactoidDaoHibernate() {
        super(Factoid.class);
    }

    @SuppressWarnings("unchecked")
    public List<Factoid> find(QueryParam qp) {
        StringBuilder query = new StringBuilder("from Factoid f");
        if(qp.hasSort()) {
            query.append(" order by ")
                .append(qp.getSort())
                .append((qp.isSortAsc()) ? " asc" : " desc");
        }
        return getEntityManager().createQuery(query.toString())
            .setFirstResult(qp.getFirst())
            .setMaxResults(qp.getCount()).getResultList();
    }

    @SuppressWarnings({"unchecked"})
    public List<Factoid> getFactoids() {
        return (List<Factoid>)getEntityManager().createNamedQuery(ALL).getResultList();
    }

    public void save(Factoid factoid) {
        factoid.setUpdated(new Date());
        super.save(factoid);
        changeDao
            .logChange(factoid.getUserName() + " changed '" + factoid.getName() + "' to '" + factoid.getValue() + "'");
    }

    public boolean hasFactoid(String key) {
        return getFactoid(key) != null;
    }

    public void addFactoid(String sender, String key, String value) {
        Factoid factoid = new Factoid();
        factoid.setName(key);
        factoid.setValue(value);
        factoid.setUserName(sender);
        factoid.setUpdated(new Date());
        super.save(factoid);
        changeDao.logAdd(sender, key, value);
    }

    public void delete(String sender, String key) {
        Factoid factoid = getFactoid(key);
        super.delete(factoid.getId());
        changeDao.logChange(sender + " removed '" + key + "'");
    }

    public Factoid getFactoid(String name) {
        Factoid factoid = null;
        try {
            factoid = (Factoid)getEntityManager().createNamedQuery(FactoidDao.BY_NAME)
                .setParameter("name", name)
                .getSingleResult();
        } catch(NoResultException e) {
        }
        return factoid;
    }

    public Long count() {
        String query = FactoidDao.COUNT;
        return (Long)getEntityManager().createQuery("select count(*) from Factoid").getSingleResult();

    }

    public Long factoidCountFiltered(Factoid filter) {
        return (Long)buildFindQuery(null, filter, true).getSingleResult();
    }

    @SuppressWarnings({"unchecked"})
    public List<Factoid> getFactoidsFiltered(QueryParam qp, Factoid filter) {
        return buildFindQuery(qp, filter, false).getResultList();
    }

    private Query buildFindQuery(QueryParam qp, Factoid filter, boolean count) {
        StringBuilder hql = new StringBuilder();
        if(count) {
            hql.append("select count(*) ");
        }
        hql.append(" from Factoid target where 1=1 ");
        if(filter.getName() != null) {
            hql.append("and upper(target.name) like :name ");
        }
        if(filter.getUserName() != null) {
            hql.append("and upper(target.userName) like :username ");
        }
        if(filter.getValue() != null) {
            hql.append("and upper(target.value) like :value ");
        }
        if(!count && qp != null && qp.hasSort()) {
            hql.append("order by upper(target.").append(qp.getSort()).append(
                ") ").append((qp.isSortAsc()) ? " asc" : " desc");
        }
        Query query = getEntityManager().createQuery(hql.toString());
        if(filter.getName() != null) {
            query.setParameter("name", "%" + filter.getName().toUpperCase() + "%");
        }
        if(filter.getUserName() != null) {
            query.setParameter("username", "%" + filter.getUserName() + "%");
        }
        if(filter.getValue() != null) {
            query.setParameter("value", "%" + filter.getValue() + "%");
        }
        if(!count && qp != null) {
            query.setFirstResult(qp.getFirst()).setMaxResults(qp.getCount());
        }
        return query;
    }

    public ChangeDao get() {
        return changeDao;
    }

    public void setChangeDao(ChangeDao dao) {
        changeDao = dao;
    }
}