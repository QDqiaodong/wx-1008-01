<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  ElAlert,
  ElButton,
  ElCard,
  ElCheckbox,
  ElCheckboxGroup,
  ElDatePicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTabs,
  ElTabPane,
  ElTag,
  ElMessage,
  type FormInstance
} from 'element-plus'
import {
  ANCHOR_ZONES,
  currentActorId,
  dutyApi,
  routeApi,
  setCurrentActorId,
  WIND_LEVELS,
  type AssignmentRequest,
  type CertificateDTO,
  type DutyAssignment,
  type DutyPolicy,
  type GroundCertificate,
  type PersonnelCertificate,
  type PersonnelDTO,
  type RouteDutyEntry,
  type FlightRoute
} from '../api'

const activeTab = ref('entry')
const policy = ref<DutyPolicy | null>(null)
const people = ref<PersonnelCertificate[]>([])
const routes = ref<FlightRoute[]>([])
const entries = ref<RouteDutyEntry[]>([])
const assignments = ref<DutyAssignment[]>([])
const selectedDate = ref(formatDate(new Date()))
const actorId = ref(currentActorId.value)
const personnelFormVisible = ref(false)
const certFormVisible = ref(false)
const assignmentVisible = ref(false)
const detailVisible = ref(false)
const editingPersonnelId = ref<number | null>(null)
const editingCertificateId = ref<number | null>(null)
const currentDetail = ref<DutyAssignment | null>(null)
const personnelFormRef = ref<FormInstance>()
const certFormRef = ref<FormInstance>()

const personnelForm = ref<PersonnelDTO>({
  employeeNo: '',
  personName: '',
  roleCode: 'OPERATOR',
  active: true
})
const certForm = ref<CertificateDTO>({
  personnelId: undefined,
  certNo: '',
  applicableWindLevels: [],
  applicableAnchorZones: [],
  effectiveDate: '',
  expiryDate: ''
})
const assignmentForm = ref<AssignmentRequest>({
  routeId: 0,
  scheduledStartAt: '',
  scheduledEndAt: '',
  operatorId: 0,
  operatorCertId: null,
  reviewerId: 0,
  reviewerCertId: null
})

const actor = computed(() => people.value.find(p => p.personnel.id === Number(actorId.value)) || null)
const isManager = computed(() => actor.value?.safetyManager === true)
const activePeople = computed(() => people.value.filter(p => p.personnel.active))
const selectedRoute = computed(() => routes.value.find(r => r.id === assignmentForm.value.routeId))
const operatorCerts = computed(() => people.value.find(p => p.personnel.id === assignmentForm.value.operatorId)?.certificates || [])
const reviewerCerts = computed(() => people.value.find(p => p.personnel.id === assignmentForm.value.reviewerId)?.certificates || [])

function formatDate(date: Date): string {
  return date.toISOString().slice(0, 10)
}

function toLocalDateTime(value: string): string {
  if (!value) return ''
  return value.length === 16 ? value + ':00' : value
}

async function loadAll() {
  await Promise.all([loadPolicy(), loadPeople(), loadRoutes()])
  await loadDutyData()
}

async function loadPolicy() {
  policy.value = await dutyApi.policy()
}

async function loadPeople() {
  people.value = await dutyApi.personnel()
}

async function loadRoutes() {
  routes.value = await routeApi.list()
}

async function loadDutyData() {
  const [entryData, assignmentData] = await Promise.all([
    dutyApi.routeEntries(selectedDate.value),
    dutyApi.assignments(selectedDate.value)
  ])
  entries.value = entryData
  assignments.value = assignmentData
}

function changeActor() {
  setCurrentActorId(actorId.value)
}

type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'
const certTagTypes: Record<string, TagType> = {
  PENDING: 'warning',
  VALID: 'success',
  EXPIRED: 'info',
  REVOKED: 'danger'
}
const assignmentTagTypes: Record<string, TagType> = {
  DRAFT: 'info',
  PENDING_REVIEW: 'warning',
  READY: 'success',
  CANCELLED: 'danger'
}

function certStatusTag(status: string): TagType {
  return certTagTypes[status] || 'info'
}

function certStatusLabel(status: string) {
  return { PENDING: '待生效', VALID: '有效', EXPIRED: '已过期', REVOKED: '已吊销' }[status] || status
}

function assignmentStatusTag(status?: string): TagType {
  return assignmentTagTypes[status || ''] || 'info'
}

function openPersonnelDialog(existing?: PersonnelCertificate) {
  editingPersonnelId.value = existing?.personnel.id || null
  personnelForm.value = existing
    ? { ...existing.personnel }
    : { employeeNo: '', personName: '', roleCode: 'OPERATOR', active: true }
  personnelFormVisible.value = true
}

async function submitPersonnel() {
  try {
    if (editingPersonnelId.value) {
      await dutyApi.updatePersonnel(editingPersonnelId.value, personnelForm.value)
      ElMessage.success('人员档案已更新；历史值守仍保留就绪时姓名快照')
    } else {
      await dutyApi.createPersonnel(personnelForm.value)
      ElMessage.success('地勤人员已创建')
    }
    personnelFormVisible.value = false
    await loadPeople()
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

function openCertDialog(person: PersonnelCertificate, cert?: GroundCertificate) {
  editingCertificateId.value = cert?.id || null
  certForm.value = cert
    ? {
        personnelId: person.personnel.id,
        certNo: cert.certNo,
        applicableWindLevels: [...cert.applicableWindLevels],
        applicableAnchorZones: [...cert.applicableAnchorZones],
        effectiveDate: cert.effectiveDate.slice(0, 10),
        expiryDate: cert.expiryDate.slice(0, 10)
      }
    : {
        personnelId: person.personnel.id,
        certNo: '',
        applicableWindLevels: [],
        applicableAnchorZones: [],
        effectiveDate: selectedDate.value,
        expiryDate: selectedDate.value
      }
  certFormVisible.value = true
}

async function submitCertificate() {
  try {
    if (editingCertificateId.value) {
      await dutyApi.updateCertificate(editingCertificateId.value, certForm.value)
      ElMessage.success('证书已更新，未来未就绪安排已按新范围重新判定')
    } else {
      await dutyApi.createCertificate(certForm.value)
      ElMessage.success('资质证已创建')
    }
    certFormVisible.value = false
    await Promise.all([loadPeople(), loadDutyData()])
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

async function revokeCertificate(cert: GroundCertificate) {
  const reason = window.prompt(`吊销证书 ${cert.certNo} 的原因？`, '现场复核发现资质不再可信')
  if (reason === null) return
  try {
    await dutyApi.revokeCertificate(cert.id, reason)
    ElMessage.success('证书已吊销；未来未就绪安排立即退回待处理，就绪/历史快照不变')
    await Promise.all([loadPeople(), loadDutyData()])
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

function openAssignmentDialog(entry?: RouteDutyEntry, existing?: DutyAssignment) {
  if (existing) {
    assignmentForm.value = {
      routeId: existing.routeId,
      scheduledStartAt: existing.scheduledStartAt.slice(0, 16),
      scheduledEndAt: existing.scheduledEndAt.slice(0, 16),
      operatorId: existing.operator.personnelId,
      operatorCertId: existing.operator.selectedCertificateId,
      reviewerId: existing.reviewer.personnelId,
      reviewerCertId: existing.reviewer.selectedCertificateId
    }
  } else {
    assignmentForm.value = {
      routeId: entry?.routeId || routes.value[0]?.id || 0,
      scheduledStartAt: `${selectedDate.value}T08:00:00`,
      scheduledEndAt: `${selectedDate.value}T09:00:00`,
      operatorId: 0,
      operatorCertId: null,
      reviewerId: 0,
      reviewerCertId: null
    }
  }
  assignmentVisible.value = true
}

async function submitAssignment() {
  try {
    const payload: AssignmentRequest = {
      ...assignmentForm.value,
      scheduledStartAt: toLocalDateTime(assignmentForm.value.scheduledStartAt),
      scheduledEndAt: toLocalDateTime(assignmentForm.value.scheduledEndAt),
      operatorCertId: assignmentForm.value.operatorCertId || null,
      reviewerCertId: assignmentForm.value.reviewerCertId || null
    }
    const saved = await dutyApi.saveAssignment(payload)
    assignmentVisible.value = false
    ElMessage.warning(saved.blockingIssues.length ? saved.blockingIssues.join('；') : '值守安排已保存')
    await loadDutyData()
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

async function showDetail(entry: RouteDutyEntry) {
  if (!entry.assignmentId) {
    openAssignmentDialog(entry)
    return
  }
  currentDetail.value = await dutyApi.assignment(entry.assignmentId)
  detailVisible.value = true
}

async function showAssignmentDetail(row: DutyAssignment) {
  currentDetail.value = await dutyApi.assignment(row.id)
  detailVisible.value = true
}

async function arrive(assignment: DutyAssignment) {
  try {
    await dutyApi.arrive(assignment.id)
    ElMessage.success('操作员已确认现场到位，进入待复核')
    await loadDutyData()
    detailVisible.value = false
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

async function ready(assignment: DutyAssignment) {
  try {
    await dutyApi.ready(assignment.id)
    ElMessage.success('已确认就绪并冻结证书快照')
    await loadDutyData()
    detailVisible.value = false
  } catch (e: any) {
    await loadDutyData()
    ElMessage.error(e.message)
  }
}

async function cancel(assignment: DutyAssignment) {
  const reason = window.prompt('安全主管取消已就绪值守的原因？', '安全条件变化')
  if (reason === null) return
  try {
    await dutyApi.cancel(assignment.id, reason)
    ElMessage.success('已就绪值守已取消')
    await loadDutyData()
    detailVisible.value = false
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

onMounted(loadAll)
</script>

<template>
  <div class="space-y-4">
    <ElAlert v-if="policy" type="info" :closable="false" show-icon>
      <div class="font-medium">{{ policy.selectedRule }}</div>
      <div class="mt-1">{{ policy.explanation }}</div>
      <div class="mt-1 text-gray-600">{{ policy.rejectedAlternative }}：{{ policy.rejectedAlternativeReason }}</div>
    </ElAlert>

    <ElCard>
      <div class="flex items-center gap-3 flex-wrap">
        <span class="font-medium">当前操作人（请求头 X-Actor-Id）：</span>
        <ElSelect v-model="actorId" style="width: 260px" @change="changeActor">
          <ElOption v-for="p in people" :key="p.personnel.id"
                    :label="`${p.personnel.personName}（${p.roleLabel}/${p.personnel.employeeNo}）`"
                    :value="p.personnel.id" />
        </ElSelect>
        <ElTag :type="isManager ? 'danger' : 'info'">{{ isManager ? '安全主管' : '普通值班员' }}</ElTag>
        <span class="text-gray-500">权限在后端服务中执行；直接调 API 但身份不符仍会被拒绝。</span>
      </div>
    </ElCard>

    <ElTabs v-model="activeTab">
      <ElTabPane label="航线入口" name="entry">
        <ElCard>
          <div class="flex items-center gap-3 mb-4">
            <span>飞行日</span>
            <ElDatePicker v-model="selectedDate" type="date" value-format="YYYY-MM-DD" @change="loadDutyData" />
          </div>
          <ElTable :data="entries" stripe>
            <ElTableColumn prop="routeCode" label="航线" />
            <ElTableColumn prop="routeName" label="名称" />
            <ElTableColumn prop="windLevel" label="当前风级" />
            <ElTableColumn label="在用锚点区域">
              <template #default="{ row }">
                <ElTag v-for="zone in row.requiredAnchorZones" :key="zone" class="mr-1">{{ zone }}</ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn label="值守">
              <template #default="{ row }">
                <ElTag v-if="row.assignmentStatus" :type="assignmentStatusTag(row.assignmentStatus)">
                  {{ row.assignmentStatusLabel }}
                </ElTag>
                <span v-else>未安排</span>
                <div class="text-xs text-gray-500">{{ row.operatorName || '—' }} / {{ row.reviewerName || '—' }}</div>
              </template>
            </ElTableColumn>
            <ElTableColumn label="同一资格结论">
              <template #default="{ row }">
                <ElTag :type="row.ready ? 'success' : row.qualified ? 'primary' : 'danger'">
                  {{ row.ready ? '就绪（历史/当前快照）' : row.qualified ? '资格满足' : '未就绪' }}
                </ElTag>
                <div v-for="issue in row.issues" :key="issue" class="text-xs text-red-600">{{ issue }}</div>
              </template>
            </ElTableColumn>
            <ElTableColumn label="操作" width="180">
              <template #default="{ row }">
                <ElButton size="small" @click="showDetail(row as RouteDutyEntry)">
                  {{ (row as RouteDutyEntry).assignmentId ? '详情' : '安排' }}
                </ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
        </ElCard>
      </ElTabPane>

      <ElTabPane label="当日值守详情" name="assignments">
        <ElCard>
          <ElTable :data="assignments" stripe>
            <ElTableColumn prop="routeCode" label="航线" />
            <ElTableColumn label="起飞/预计结束">
              <template #default="{ row }">
                {{ row.scheduledStartAt }}<br />至 {{ row.scheduledEndAt }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="操作员">
              <template #default="{ row }">
                {{ row.operator.personName }}
                <ElTag size="small" :type="row.operator.qualified ? 'success' : 'danger'">
                  {{ row.operator.qualified ? '资格满足' : '缺资格' }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn label="复核员">
              <template #default="{ row }">
                {{ row.reviewer.personName }}
                <ElTag size="small" :type="row.reviewer.qualified ? 'success' : 'danger'">
                  {{ row.reviewer.qualified ? '资格满足' : '缺资格' }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn label="状态">
              <template #default="{ row }">
                <ElTag :type="assignmentStatusTag(row.status)">{{ row.statusLabel }}</ElTag>
                <div class="text-xs">{{ row.operatorArrived ? '操作员已到位' : '操作员未到位' }}</div>
              </template>
            </ElTableColumn>
            <ElTableColumn label="操作">
              <template #default="{ row }">
                <ElButton size="small" @click="showAssignmentDetail(row as DutyAssignment)">详情</ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
        </ElCard>
      </ElTabPane>

      <ElTabPane label="人员与资质" name="people">
        <ElCard>
          <div class="flex justify-between mb-4">
            <h3 class="font-semibold">人员列表（同一跨午夜口径：按计划起飞时刻判证）</h3>
            <ElButton type="primary" @click="openPersonnelDialog()">新增人员</ElButton>
          </div>
          <ElTable :data="people" stripe>
            <ElTableColumn label="人员">
              <template #default="{ row }">
                {{ row.personnel.personName }} / {{ row.personnel.employeeNo }}
                <ElTag size="small" :type="row.safetyManager ? 'danger' : 'info'">{{ row.roleLabel }}</ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn label="资质证（风级 / 区域 / 有效期 / 状态）">
              <template #default="{ row }">
                <div v-for="cert in row.certificates" :key="cert.id" class="border-b py-2">
                  <div class="font-medium">{{ cert.certNo }}
                    <ElTag size="small" :type="certStatusTag(cert.status)">{{ certStatusLabel(cert.status) }}</ElTag>
                  </div>
                  <div class="text-xs">风级：{{ cert.applicableWindLevels.join('、') }}</div>
                  <div class="text-xs">区域：{{ cert.applicableAnchorZones.join('、') }}</div>
                  <div class="text-xs">{{ cert.effectiveDate }} 至 {{ cert.expiryDate }}</div>
                  <div v-if="cert.status === 'REVOKED'" class="text-xs text-red-600">
                    吊销：{{ cert.revokeReason }}（{{ cert.revokedByName }}）
                  </div>
                  <div class="mt-1">
                    <ElButton size="small" :disabled="cert.status === 'REVOKED'" @click="openCertDialog(row as PersonnelCertificate, cert as GroundCertificate)">编辑</ElButton>
                    <ElButton size="small" type="danger" :disabled="!isManager || cert.status === 'REVOKED'"
                              @click="revokeCertificate(cert as GroundCertificate)">吊销</ElButton>
                  </div>
                </div>
                <div v-if="row.certificates.length === 0" class="text-gray-400">暂无证书</div>
              </template>
            </ElTableColumn>
            <ElTableColumn label="操作" width="160">
              <template #default="{ row }">
                <ElButton size="small" @click="openPersonnelDialog(row as PersonnelCertificate)">编辑档案</ElButton>
                <ElButton size="small" type="primary" @click="openCertDialog(row as PersonnelCertificate)">发证</ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
        </ElCard>
      </ElTabPane>
    </ElTabs>

    <ElDialog v-model="personnelFormVisible" title="地勤人员" width="480px">
      <ElForm :model="personnelForm" ref="personnelFormRef" label-width="90px">
        <ElFormItem label="工号" required><ElInput v-model="personnelForm.employeeNo" /></ElFormItem>
        <ElFormItem label="姓名" required><ElInput v-model="personnelForm.personName" /></ElFormItem>
        <ElFormItem label="角色">
          <ElSelect v-model="personnelForm.roleCode">
            <ElOption label="普通值班员" value="OPERATOR" />
            <ElOption label="安全主管" value="SAFETY_MANAGER" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="在职"><ElCheckbox v-model="personnelForm.active">在职</ElCheckbox></ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="personnelFormVisible = false">取消</ElButton>
        <ElButton type="primary" @click="submitPersonnel">保存</ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="certFormVisible" title="地勤资质证" width="640px">
      <ElForm :model="certForm" ref="certFormRef" label-width="110px">
        <ElFormItem label="持证人" required>
          <ElSelect v-model="certForm.personnelId" style="width: 100%">
            <ElOption v-for="p in people" :key="p.personnel.id" :label="p.personnel.personName" :value="p.personnel.id" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="证书编号" required><ElInput v-model="certForm.certNo" /></ElFormItem>
        <ElFormItem label="适用风级" required>
          <ElCheckboxGroup v-model="certForm.applicableWindLevels">
            <ElCheckbox v-for="level in WIND_LEVELS" :key="level" :label="level" />
          </ElCheckboxGroup>
        </ElFormItem>
        <ElFormItem label="锚点区域" required>
          <ElCheckboxGroup v-model="certForm.applicableAnchorZones">
            <ElCheckbox v-for="zone in ANCHOR_ZONES" :key="zone" :label="zone" />
          </ElCheckboxGroup>
        </ElFormItem>
        <ElFormItem label="生效日" required>
          <ElDatePicker v-model="certForm.effectiveDate" type="date" value-format="YYYY-MM-DD" />
        </ElFormItem>
        <ElFormItem label="到期日" required>
          <ElDatePicker v-model="certForm.expiryDate" type="date" value-format="YYYY-MM-DD" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="certFormVisible = false">取消</ElButton>
        <ElButton type="primary" @click="submitCertificate">保存并重判未来值守</ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="assignmentVisible" title="安排开航值守" width="720px">
      <ElForm :model="assignmentForm" label-width="120px">
        <ElFormItem label="航线" required>
          <ElSelect v-model="assignmentForm.routeId" style="width: 100%">
            <ElOption v-for="r in routes" :key="r.id" :label="`${r.routeCode} ${r.routeName}（${r.windLevel}）`" :value="r.id" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="计划起飞" required>
          <ElDatePicker v-model="assignmentForm.scheduledStartAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" />
        </ElFormItem>
        <ElFormItem label="预计结束" required>
          <ElDatePicker v-model="assignmentForm.scheduledEndAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" />
        </ElFormItem>
        <ElFormItem label="操作员" required>
          <ElSelect v-model="assignmentForm.operatorId" style="width: 100%">
            <ElOption v-for="p in activePeople" :key="p.personnel.id" :label="p.personnel.personName" :value="p.personnel.id" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="操作员证书">
          <ElSelect v-model="assignmentForm.operatorCertId" clearable placeholder="留空自动选最适合有效证" style="width: 100%">
            <ElOption v-for="c in operatorCerts" :key="c.id"
                      :label="`${c.certNo}（${certStatusLabel(c.status)} ${c.applicableAnchorZones.join('/')}）`" :value="c.id" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="复核员" required>
          <ElSelect v-model="assignmentForm.reviewerId" style="width: 100%">
            <ElOption v-for="p in activePeople" :key="p.personnel.id" :label="p.personnel.personName" :value="p.personnel.id" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="复核员证书">
          <ElSelect v-model="assignmentForm.reviewerCertId" clearable placeholder="留空自动选最适合有效证" style="width: 100%">
            <ElOption v-for="c in reviewerCerts" :key="c.id"
                      :label="`${c.certNo}（${certStatusLabel(c.status)} ${c.applicableAnchorZones.join('/')}）`" :value="c.id" />
          </ElSelect>
        </ElFormItem>
        <ElAlert v-if="selectedRoute" type="info" :closable="false"
                 :title="`当前风级 ${selectedRoute.windLevel}；跨午夜任务只按起飞时刻判断证书有效性。`" />
      </ElForm>
      <template #footer>
        <ElButton @click="assignmentVisible = false">取消</ElButton>
        <ElButton type="primary" @click="submitAssignment">保存并判定</ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="detailVisible" title="值守详情与状态链" width="820px">
      <div v-if="currentDetail" class="space-y-4">
        <ElAlert :type="currentDetail.readyAllowed || currentDetail.status === 'READY' ? 'success' : 'warning'"
                 :closable="false" :title="currentDetail.midnightPolicy" />
        <div class="grid grid-cols-2 gap-3 text-sm">
          <div>航线：{{ currentDetail.routeCode }} {{ currentDetail.routeName }}</div>
          <div>风级：{{ currentDetail.routeWindLevel }}</div>
          <div>起飞：{{ currentDetail.scheduledStartAt }}</div>
          <div>预计结束：{{ currentDetail.scheduledEndAt }}</div>
          <div>状态：<ElTag :type="assignmentStatusTag(currentDetail.status)">{{ currentDetail.statusLabel }}</ElTag></div>
          <div>操作员到位：{{ currentDetail.operatorArrived ? '已到位' : '未到位' }}</div>
        </div>
        <div v-for="role in [currentDetail.operator, currentDetail.reviewer]" :key="role.role" class="border rounded p-3">
          <div class="font-medium">{{ role.roleLabel }}：{{ role.personName }}
            <ElTag :type="role.qualified ? 'success' : 'danger'">{{ role.qualified ? '覆盖' : '不覆盖' }}</ElTag>
          </div>
          <div v-if="role.currentSnapshotUsed && role.readySnapshot" class="text-sm bg-green-50 p-2 mt-2">
            历史快照（不随后续档案变化）：证书 {{ role.readySnapshot.certificateNo }}，
            姓名 {{ role.readySnapshot.personName }}，
            风级 {{ role.readySnapshot.applicableWindLevels.join('、') }}，
            区域 {{ role.readySnapshot.applicableAnchorZones.join('、') }}，
            {{ role.readySnapshot.effectiveDate }} 至 {{ role.readySnapshot.expiryDate }}
          </div>
          <ElTable v-else :data="role.certificateChecks" size="small">
            <ElTableColumn prop="certificateNo" label="证书" />
            <ElTableColumn label="状态">
              <template #default="{ row }">{{ certStatusLabel(row.status) }}</template>
            </ElTableColumn>
            <ElTableColumn label="逐项缺口">
              <template #default="{ row }">
                <div v-for="reason in row.reasons" :key="reason" class="text-red-600">{{ reason }}</div>
                <span v-if="row.reasons.length === 0" class="text-green-600">风级和全部在用锚点区域均覆盖</span>
              </template>
            </ElTableColumn>
          </ElTable>
          <div v-for="issue in role.missingReasons" :key="issue" class="text-red-600 text-sm">{{ issue }}</div>
        </div>
        <div>
          <div class="font-medium mb-1">阻断项</div>
          <div v-for="issue in currentDetail.blockingIssues" :key="issue" class="text-red-600">• {{ issue }}</div>
          <div v-if="currentDetail.blockingIssues.length === 0" class="text-green-600">无阻断项</div>
        </div>
        <div class="flex justify-end gap-2">
          <ElButton v-if="currentDetail.status !== 'READY' && currentDetail.status !== 'CANCELLED'"
                    @click="openAssignmentDialog(undefined, currentDetail); detailVisible = false">编辑安排</ElButton>
          <ElButton v-if="currentDetail.status !== 'READY' && currentDetail.status !== 'CANCELLED'"
                    type="primary"
                    :disabled="currentDetail.operator.personnelId !== Number(actorId)"
                    @click="arrive(currentDetail)">操作员确认我已到位</ElButton>
          <ElButton v-if="currentDetail.status === 'PENDING_REVIEW'"
                    type="success"
                    :disabled="currentDetail.reviewer.personnelId !== Number(actorId)"
                    @click="ready(currentDetail)">复核员确认就绪</ElButton>
          <ElButton v-if="currentDetail.status === 'READY'" type="danger" :disabled="!isManager"
                    @click="cancel(currentDetail)">安全主管取消就绪</ElButton>
        </div>
        <div class="text-xs text-gray-500">
          操作员只能做自己的到位确认；复核员不能等于操作员；吊销与取消已就绪由后端强制安全主管身份。
        </div>
      </div>
    </ElDialog>
  </div>
</template>
