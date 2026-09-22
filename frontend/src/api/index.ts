import axios, { type AxiosError, type AxiosResponse } from 'axios'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

request.interceptors.request.use((config) => {
  if (currentActorId.value) {
    config.headers['X-Actor-Id'] = currentActorId.value
  }
  return config
})

request.interceptors.response.use(
  (response: AxiosResponse) => {
    const res = response.data
    if (res.code !== 200) {
      return Promise.reject(new Error(res.message || 'Error'))
    }
    return res.data
  },
  (error: AxiosError<any>) => {
    const message = error.response?.data?.message || error.message || '请求失败'
    return Promise.reject(new Error(message))
  }
)

const get = <T>(url: string, config = {}) => request.get<T>(url, config) as unknown as Promise<T>
const post = <T>(url: string, data?: unknown, config = {}) => request.post<T>(url, data, config) as unknown as Promise<T>
const put = <T>(url: string, data?: unknown) => request.put<T>(url, data) as unknown as Promise<T>
const del = (url: string) => request.delete(url) as unknown as Promise<void>

export interface Anchor {
  id: number
  anchorCode: string
  maxWeight: number
  minWindSpeed: number
  maxWindSpeed: number
  anchorZone: string
  locationDesc: string
  status: number
  createTime: string
  updateTime: string
}

export interface AnchorDTO {
  anchorCode: string
  maxWeight: number
  minWindSpeed: number
  maxWindSpeed: number
  anchorZone: string
  locationDesc: string
}

export interface FlightRoute {
  id: number
  routeCode: string
  routeName: string
  routeGroup: string
  windSpeed: number
  windLevel: string
  description: string
  status: number
  createTime: string
  updateTime: string
}

export interface RouteDTO {
  routeCode: string
  routeName: string
  routeGroup: string
  windSpeed: number
  description: string
}

export interface AdaptResult {
  valid: boolean
  routeId: number
  anchorId: number
  bindId: number
  reason: string
  rebindCount: number
  unbindCount: number
  logIds: number[]
}

export interface RouteAnchor {
  id: number
  routeId: number
  anchorId: number
  bindTime: string
  unbindTime: string
  status: number
}

export interface AdaptLog {
  id: number
  routeId: number
  routeCode: string
  anchorId: number
  anchorCode: string
  operationType: string
  beforeWindSpeed: number
  afterWindSpeed: number
  beforeWeight: number
  afterWeight: number
  reason: string
  operator: string
  createTime: string
}

export interface GroupAnchorResult {
  anchorId: number
  anchorCode: string
  maxWeight: number
  minWindSpeed: number
  maxWindSpeed: number
  eligible: boolean
  failedChecks: string[]
  reasons: string[]
  occupiedByRouteCode: string | null
}

export interface GroupRehearseResult {
  routeId: number
  routeCode: string
  routeName: string
  routeWindSpeed: number
  windLevel: string
  requiredMinWeight: number
  totalCount: number
  eligibleCount: number
  rejectedCount: number
  eligibleTotalWeight: number
  requiredTotalWeight: number
  totalWeightBudgetOk: boolean
  groupValid: boolean
  policy: string
  policyNotice: string
  anchorResults: GroupAnchorResult[]
  weightRuleTable: Record<string, number>
}

export interface GroupSubmitResult {
  routeId: number
  routeCode: string
  committed: boolean
  bindIds: number[]
  submittedCount: number
  rehearsal: GroupRehearseResult
  message: string
  logIds: number[]
}

export interface GroundPersonnel {
  id: number
  employeeNo: string
  personName: string
  roleCode: 'OPERATOR' | 'SAFETY_MANAGER'
  active: boolean
}

export interface GroundCertificate {
  id: number
  certNo: string
  personnelId: number
  personName: string
  applicableWindLevels: string[]
  applicableAnchorZones: string[]
  effectiveDate: string
  expiryDate: string
  status: 'PENDING' | 'VALID' | 'EXPIRED' | 'REVOKED'
  revokedAt?: string
  revokedByName?: string
  revokeReason?: string
}

export interface PersonnelCertificate {
  personnel: GroundPersonnel
  roleLabel: string
  safetyManager: boolean
  certificates: GroundCertificate[]
}

export interface CertificateDTO {
  personnelId?: number
  certNo: string
  applicableWindLevels: string[]
  applicableAnchorZones: string[]
  effectiveDate: string
  expiryDate: string
}

export interface PersonnelDTO {
  employeeNo: string
  personName: string
  roleCode: 'OPERATOR' | 'SAFETY_MANAGER'
  active: boolean
}

export interface DutySnapshot {
  personnelId: number
  personName: string
  certificateId: number
  certificateNo: string
  applicableWindLevels: string[]
  applicableAnchorZones: string[]
  effectiveDate: string
  expiryDate: string
  certificateStatusAtReady: string
  qualificationDateAtReady: string
}

export interface CertificateCheck {
  certificateId: number
  certificateNo: string
  status: string
  applicableWindLevels: string[]
  applicableAnchorZones: string[]
  effectiveDate: string
  expiryDate: string
  selected: boolean
  eligible: boolean
  missingWindLevels: string[]
  missingAnchorZones: string[]
  failureCodes: string[]
  reasons: string[]
}

export interface RoleQualification {
  role: 'OPERATOR' | 'REVIEWER'
  roleLabel: string
  personnelId: number
  personName: string
  selectedCertificateId: number | null
  selectedCertificateNo: string | null
  qualified: boolean
  currentSnapshotUsed: boolean
  certificateChecks: CertificateCheck[]
  missingReasons: string[]
  readySnapshot?: DutySnapshot
}

export interface RouteAnchorZone {
  anchorId: number
  anchorCode: string
  anchorZone: string
  active: boolean
}

export interface DutyAssignment {
  id: number
  routeId: number
  routeCode: string
  routeName: string
  routeWindLevel: string
  flightDate: string
  scheduledStartAt: string
  scheduledEndAt: string
  status: 'DRAFT' | 'PENDING_REVIEW' | 'READY' | 'CANCELLED'
  statusLabel: string
  operatorArrived: boolean
  arrivedAt?: string
  readyAt?: string
  cancelledAt?: string
  cancelReason?: string
  operator: RoleQualification
  reviewer: RoleQualification
  requiredAnchorZones: string[]
  routeAnchors: RouteAnchorZone[]
  blockingIssues: string[]
  readyAllowed: boolean
  historical: boolean
  midnightPolicy: string
  midnightPolicyRejectedAlternative: string
}

export interface AssignmentRequest {
  routeId: number
  scheduledStartAt: string
  scheduledEndAt: string
  operatorId: number
  operatorCertId?: number | null
  reviewerId: number
  reviewerCertId?: number | null
}

export interface RouteDutyEntry {
  routeId: number
  routeCode: string
  routeName: string
  windLevel: string
  flightDate: string
  assignmentId?: number
  assignmentStatus?: string
  assignmentStatusLabel?: string
  operatorName?: string
  reviewerName?: string
  operatorArrived: boolean
  requiredAnchorZones: string[]
  qualified: boolean
  ready: boolean
  issues: string[]
}

export interface DutyPolicy {
  selectedRuleCode: string
  selectedRule: string
  explanation: string
  rejectedAlternative: string
  rejectedAlternativeReason: string
  statusChain: string
}

export const WIND_LEVELS = ['微风', '轻风', '和风', '强风', '疾风']
export const ANCHOR_ZONES = ['东区', '南区', '西区', '北区']

const ACTOR_KEY = 'px.actorId'
export const currentActorId = { value: localStorage.getItem(ACTOR_KEY) || '' }
export const setCurrentActorId = (id: string) => {
  currentActorId.value = id
  if (id) localStorage.setItem(ACTOR_KEY, id)
  else localStorage.removeItem(ACTOR_KEY)
}

export const anchorApi = {
  list: () => get<Anchor[]>('/anchor'),
  get: (id: number) => get<Anchor>(`/anchor/${id}`),
  create: (data: AnchorDTO) => post<Anchor>('/anchor', data),
  update: (id: number, data: AnchorDTO) => put<Anchor>(`/anchor/${id}`, data),
  delete: (id: number) => del(`/anchor/${id}`),
  filter: (minWind: number, maxWind: number) => get<Anchor[]>(`/anchor/filter?minWind=${minWind}&maxWind=${maxWind}`)
}

export const routeApi = {
  list: () => get<FlightRoute[]>('/route'),
  get: (id: number) => get<FlightRoute>(`/route/${id}`),
  create: (data: RouteDTO) => post<FlightRoute>('/route', data),
  update: (id: number, data: RouteDTO) => put<FlightRoute>(`/route/${id}`, data),
  delete: (id: number) => del(`/route/${id}`),
  groups: () => get<string[]>('/route/groups')
}

export const adaptApi = {
  bind: (routeId: number, anchorId: number) => post<AdaptResult>('/adapt/bind', { routeId, anchorId }),
  unbind: (routeId: number, anchorId: number) => post<AdaptResult>('/adapt/unbind', { routeId, anchorId }),
  check: (routeId: number, anchorId: number) => get<AdaptResult>(`/adapt/check?routeId=${routeId}&anchorId=${anchorId}`),
  recheck: (routeId: number) => post<AdaptResult>(`/adapt/recheck/${routeId}`),
  logs: (routeId?: number, anchorId?: number) => {
    let url = '/adapt/logs'
    if (routeId) url += `?routeId=${routeId}`
    else if (anchorId) url += `?anchorId=${anchorId}`
    return get<AdaptLog[]>(url)
  },
  bound: (routeId: number) => get<RouteAnchor[]>(`/adapt/bound/${routeId}`)
}

export const groupBindingApi = {
  rules: () => get<Record<string, number>>('/group-binding/rules'),
  rehearse: (routeId: number, anchorIds: number[], operator?: string) =>
    post<GroupRehearseResult>('/group-binding/rehearse', { routeId, anchorIds, operator }),
  submit: (routeId: number, anchorIds: number[], operator?: string) =>
    post<GroupSubmitResult>('/group-binding/submit', { routeId, anchorIds, operator })
}

export const dutyApi = {
  policy: () => get<DutyPolicy>('/duty/policy'),
  personnel: () => get<PersonnelCertificate[]>('/duty/personnel'),
  createPersonnel: (data: PersonnelDTO) => post<GroundPersonnel>('/duty/personnel', data),
  updatePersonnel: (id: number, data: PersonnelDTO) => put<GroundPersonnel>(`/duty/personnel/${id}`, data),
  createCertificate: (data: CertificateDTO) => post<GroundCertificate>('/duty/certificates', data),
  updateCertificate: (id: number, data: CertificateDTO) => put<GroundCertificate>(`/duty/certificates/${id}`, data),
  revokeCertificate: (id: number, reason: string) =>
    post<GroundCertificate>(`/duty/certificates/${id}/revoke`, { reason }),
  assignments: (date?: string) => get<DutyAssignment[]>(`/duty/assignments${date ? `?date=${date}` : ''}`),
  assignment: (id: number) => get<DutyAssignment>(`/duty/assignments/${id}`),
  saveAssignment: (data: AssignmentRequest) => post<DutyAssignment>('/duty/assignments', data),
  arrive: (id: number) => post<DutyAssignment>(`/duty/assignments/${id}/arrive`),
  ready: (id: number) => post<DutyAssignment>(`/duty/assignments/${id}/ready`),
  cancel: (id: number, reason: string) => post<DutyAssignment>(`/duty/assignments/${id}/cancel`, { reason }),
  routeEntries: (date?: string) => get<RouteDutyEntry[]>(`/duty/route-entries${date ? `?date=${date}` : ''}`)
}
