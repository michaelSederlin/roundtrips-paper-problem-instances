# Used to remove dependence on scipy since it's such a large dependency

import numpy as np
from collections import namedtuple
KSResult = namedtuple("KSResult", "statistic pvalue statistic_location statistic_sign")

def wasserstein_distance(u, v):
    u = np.sort(np.asarray(u, float))
    v = np.sort(np.asarray(v, float))
    # merge support, integrate |CDF_u - CDF_v|
    allv = np.concatenate([u, v])
    allv.sort()
    du = np.searchsorted(u, allv, side="right") / u.size
    dv = np.searchsorted(v, allv, side="right") / v.size
    deltas = np.diff(allv)
    return np.sum(np.abs(du[:-1] - dv[:-1]) * deltas)

def ks_2samp(u, v):
    u = np.sort(np.asarray(u, float))
    v = np.sort(np.asarray(v, float))
    allv = np.concatenate([u, v])
    allv.sort()
    cdf_u = np.searchsorted(u, allv, side="right") / u.size
    cdf_v = np.searchsorted(v, allv, side="right") / v.size
    diff = cdf_u - cdf_v                          # signed
    i = np.argmax(np.abs(diff))
    stat = float(np.abs(diff[i]))
    loc = float(allv[i])
    sign = int(np.sign(diff[i])) or 1             # +1 on exact tie, matching scipy
    p = ks_pvalue(stat, u.size, v.size)
    return KSResult(stat, p, loc, sign)

def ks_pvalue(d, n1, n2):
    en = (n1 * n2 / (n1 + n2)) ** 0.5
    lam = (en + 0.12 + 0.11 / en) * d           # Stephens' correction
    # Q_KS(lam) = 2 * sum_{k=1}^inf (-1)^{k-1} exp(-2 k^2 lam^2)
    k = np.arange(1, 101)
    return 2.0 * np.sum((-1) ** (k - 1) * np.exp(-2.0 * k**2 * lam**2))

def rel_entr(x, y):
    x = np.asarray(x, dtype=float)
    y = np.asarray(y, dtype=float)
    out = np.full(np.broadcast(x, y).shape, np.inf)   # default: +inf
    # x == 0, y >= 0  -> 0
    zero = (x == 0) & (y >= 0)
    out[zero] = 0.0
    # x > 0, y > 0     -> x*log(x/y)
    pos = (x > 0) & (y > 0)
    out[pos] = x[pos] * np.log(x[pos] / y[pos])
    return out
def jensenshannon(p, q, base=None, *, axis=0, keepdims=False):
    """
    Compute the Jensen-Shannon distance (metric) between
    two probability arrays. This is the square root
    of the Jensen-Shannon divergence.

    The Jensen-Shannon distance between two probability
    vectors `p` and `q` is defined as,

    .. math::

       \\sqrt{\\frac{D(p \\parallel m) + D(q \\parallel m)}{2}}

    where :math:`m` is the pointwise mean of :math:`p` and :math:`q`
    and :math:`D` is the Kullback-Leibler divergence.

    This routine will normalize `p` and `q` if they don't sum to 1.0.

    Parameters
    ----------
    p : (N,) array_like
        left probability vector
    q : (N,) array_like
        right probability vector
    base : double, optional
        the base of the logarithm used to compute the calibration
        if not given, then the routine uses the default base of
        scipy.stats.entropy.
    axis : int, optional
        Axis along which the Jensen-Shannon distances are computed. The default
        is 0.

        .. versionadded:: 1.7.0
    keepdims : bool, optional
        If this is set to `True`, the reduced axes are left in the
        result as dimensions with size one. With this option,
        the result will broadcast correctly against the old1 array.
        Default is False.

        .. versionadded:: 1.7.0

    Returns
    -------
    js : double or ndarray
        The Jensen-Shannon distances between `p` and `q` along the `axis`.

    Notes
    -----

    .. versionadded:: 1.2.0

    Examples
    --------
    >>> from scipy.spatial import distance
    >>> import numpy as np
    >>> distance.jensenshannon([1.0, 0.0, 0.0], [0.0, 1.0, 0.0], 2.0)
    1.0
    >>> distance.jensenshannon([1.0, 0.0], [0.5, 0.5])
    0.46450140402245893
    >>> distance.jensenshannon([1.0, 0.0, 0.0], [1.0, 0.0, 0.0])
    0.0
    >>> a = np.array([[1, 2, 3, 4],
    ...               [5, 6, 7, 8],
    ...               [9, 10, 11, 12]])
    >>> b = np.array([[13, 14, 15, 16],
    ...               [17, 18, 19, 20],
    ...               [21, 22, 23, 24]])
    >>> distance.jensenshannon(a, b, axis=0)
    array([0.1954288, 0.1447697, 0.1138377, 0.0927636])
    >>> distance.jensenshannon(a, b, axis=1)
    array([0.1402339, 0.0399106, 0.0201815])

    """
    p = np.asarray(p)
    q = np.asarray(q)
    p = p / np.sum(p, axis=axis, keepdims=True)
    q = q / np.sum(q, axis=axis, keepdims=True)
    m = (p + q) / 2.0
    left = rel_entr(p, m)
    right = rel_entr(q, m)
    left_sum = np.sum(left, axis=axis, keepdims=keepdims)
    right_sum = np.sum(right, axis=axis, keepdims=keepdims)
    js = left_sum + right_sum
    if base is not None:
        js /= np.log(base)
    return np.sqrt(js / 2.0)

