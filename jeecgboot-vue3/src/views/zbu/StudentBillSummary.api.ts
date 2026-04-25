import {defHttp} from '/@/utils/http/axios';
import { useMessage } from "/@/hooks/web/useMessage";

const { createConfirm } = useMessage();

export const summaryList = (params) => {
  return defHttp.get({ url: '/zbu/studentBill/summary', params }, { joinParamsToUrl: true });
};

export const getStudentByNo = (studentNo) => {
  return defHttp.get({ url: '/zbu/tStudent/queryByStudentNo', params: { studentNo } });
};

export const batchDelete = (params, handleSuccess) => {
  createConfirm({
    iconType: 'warning',
    title: '确认删除',
    content: '是否删除选中数据',
    okText: '确认',
    cancelText: '取消',
    onOk: () => {
      return defHttp.delete({url: '/zbu/studentBill/deleteBatch', data: params}, {joinParamsToUrl: true}).then(() => {
        handleSuccess();
      });
    }
  });
}
